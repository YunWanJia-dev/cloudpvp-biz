package mq

import (
	"fmt"
	"log/slog"

	amqp "github.com/rabbitmq/amqp091-go"
)

const (
	// MatchCreateRoutingKey is published by matcher after teams are finalized.
	MatchCreateRoutingKey = "match.create"
	// MatchUpdateRoutingKey is published after allocation changes the complete match.
	MatchUpdateRoutingKey = "match.update"
	// AllocatorQueue is the shared competing-consumer queue for server allocation.
	AllocatorQueue = "match.server-allocator.queue"
)

// DeclareTopology declares the allocator-owned queue on the shared topic exchange.
func DeclareTopology(connection *amqp.Connection, exchangeName string) error {
	channel, err := connection.Channel()
	if err != nil {
		return fmt.Errorf("create topology channel: %w", err)
	}
	defer channel.Close()
	if err := channel.ExchangeDeclare(exchangeName, "topic", true, false, false, false, nil); err != nil {
		return fmt.Errorf("declare exchange: %w", err)
	}
	if _, err := channel.QueueDeclare(AllocatorQueue, true, false, false, false, nil); err != nil {
		return fmt.Errorf("declare allocator queue: %w", err)
	}
	if err := channel.QueueBind(AllocatorQueue, MatchCreateRoutingKey, exchangeName, false, nil); err != nil {
		return fmt.Errorf("bind allocator queue: %w", err)
	}
	slog.Info("RabbitMQ queue bound", "exchange", exchangeName, "queue", AllocatorQueue, "routing_key", MatchCreateRoutingKey)
	return nil
}
