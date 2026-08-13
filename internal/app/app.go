package app

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"log/slog"
	"os"
	"os/signal"
	"syscall"

	"server-allocator/internal/domain/match"
	infraallocator "server-allocator/internal/infra/allocator"
	localconfig "server-allocator/internal/infra/config"
	"server-allocator/internal/infra/mq"
	"server-allocator/internal/infra/mq/publisher"
	allocationusecase "server-allocator/internal/usecase/allocation"

	amqp "github.com/rabbitmq/amqp091-go"
)

// Options configures server allocator startup.
type Options struct {
	ConfigPath string
}

// Run wires infrastructure and blocks until shutdown.
func Run(ctx context.Context, options Options) error {
	slog.SetDefault(slog.New(slog.NewTextHandler(os.Stdout, &slog.HandlerOptions{Level: slog.LevelInfo})))
	runCtx, stop := signal.NotifyContext(ctx, syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	configPath := options.ConfigPath
	if configPath == "" {
		configPath = "config.yaml"
	}
	if _, err := os.Stat(configPath); os.IsNotExist(err) {
		if err := localconfig.GenerateLocalAppConfig(configPath); err != nil {
			return err
		}
		return fmt.Errorf("local config did not exist; generated %s", configPath)
	}
	appConfig, err := localconfig.LoadLocalAppConfig(configPath)
	if err != nil {
		return fmt.Errorf("load local Apollo config: %w", err)
	}
	apolloClient := localconfig.NewApolloClient(appConfig.Apollo)
	defer apolloClient.Close()
	rabbitMQConfig, err := localconfig.Get[mq.RabbitMQConfig](apolloClient)
	if err != nil {
		return fmt.Errorf("read RabbitMQ config: %w", err)
	}
	connection, err := mq.NewRabbitMQConnection(rabbitMQConfig)
	if err != nil {
		return err
	}
	defer connection.Close()
	if err := mq.DeclareTopology(connection, rabbitMQConfig.ExchangeName); err != nil {
		return err
	}
	consumeChannel, err := connection.Channel()
	if err != nil {
		return err
	}
	defer consumeChannel.Close()
	if err := consumeChannel.Qos(1, 0, false); err != nil {
		return err
	}
	publishChannel, err := connection.Channel()
	if err != nil {
		return err
	}
	defer publishChannel.Close()
	newPublisher, err := publisher.NewPublisher(publishChannel, rabbitMQConfig)
	if err != nil {
		return err
	}
	usecase := allocationusecase.NewUseCase(infraallocator.NewFixedAllocator(), newPublisher)
	return runConsumer(runCtx, consumeChannel, usecase)
}

// runConsumer consumes match.create and ACKs only after confirmed match.update publication.
func runConsumer(ctx context.Context, channel *amqp.Channel, usecase *allocationusecase.UseCase) error {
	deliveries, err := channel.Consume(mq.AllocatorQueue, "", false, false, false, false, nil)
	if err != nil {
		return fmt.Errorf("consume match.create: %w", err)
	}
	slog.Info("server allocator started", "queue", mq.AllocatorQueue, "routing_key", mq.MatchCreateRoutingKey)
	for {
		select {
		case <-ctx.Done():
			return ctx.Err()
		case delivery, ok := <-deliveries:
			if !ok {
				return fmt.Errorf("match.create delivery channel closed")
			}
			handleDelivery(ctx, usecase, delivery)
		}
	}
}

// handleDelivery validates, allocates and publishes the complete updated match.
func handleDelivery(ctx context.Context, usecase *allocationusecase.UseCase, delivery amqp.Delivery) {
	var value match.Match
	if err := json.Unmarshal(delivery.Body, &value); err != nil {
		slog.Error("reject malformed match.create", "error", err, "delivery_tag", delivery.DeliveryTag)
		_ = delivery.Nack(false, false)
		return
	}
	slog.Info("match.create received", "match_id", value.MatchID, "team_count", len(value.Teams), "redelivered", delivery.Redelivered)
	if err := value.ValidateCreate(); err != nil {
		slog.Error("reject invalid match.create", "match_id", value.MatchID, "error", err)
		_ = delivery.Nack(false, false)
		return
	}
	if err := usecase.Allocate(ctx, &value); err != nil {
		// Validation is complete, so allocation or publication failures remain retryable infrastructure errors.
		slog.Error("allocation or match.update publication failed; requeue create", "match_id", value.MatchID, "error", err)
		_ = delivery.Nack(false, true)
		return
	}
	if err := delivery.Ack(false); err != nil {
		log.Printf("ack match.create failed match_id=%s: %v", value.MatchID, err)
		return
	}
	slog.Info("match.create acknowledged", "match_id", value.MatchID, "status", value.Status)
}
