package publisher

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"time"

	"server-allocator/internal/domain/allocation"
	"server-allocator/internal/domain/match"
	"server-allocator/internal/infra/mq"

	amqp "github.com/rabbitmq/amqp091-go"
)

// Publisher publishes allocation updates through RabbitMQ.
type Publisher struct {
	channel  *amqp.Channel
	exchange string
}

var _ allocation.Publisher = (*Publisher)(nil)

// NewPublisher creates a confirmed RabbitMQ publisher.
func NewPublisher(channel *amqp.Channel, config *mq.RabbitMQConfig) (*Publisher, error) {
	if err := channel.Confirm(false); err != nil {
		return nil, fmt.Errorf("enable publisher confirms: %w", err)
	}
	return &Publisher{channel: channel, exchange: config.ExchangeName}, nil
}

// PublishMatchUpdate publishes the complete updated match and waits for broker confirmation.
func (p *Publisher) PublishMatchUpdate(ctx context.Context, value *match.Match) error {
	body, err := json.Marshal(value)
	if err != nil {
		return fmt.Errorf("marshal match.update: %w", err)
	}
	confirmation, err := p.channel.PublishWithDeferredConfirmWithContext(
		ctx,
		p.exchange,
		mq.MatchUpdateRoutingKey,
		true,
		false,
		amqp.Publishing{ContentType: "application/json", DeliveryMode: amqp.Persistent, Timestamp: time.Now(), Body: body},
	)
	if err != nil {
		return fmt.Errorf("publish match.update: %w", err)
	}
	acknowledged, err := confirmation.WaitContext(ctx)
	if err != nil {
		return fmt.Errorf("wait match.update confirm: %w", err)
	}
	if !acknowledged {
		return fmt.Errorf("broker rejected match.update match_id=%s", value.MatchID)
	}
	slog.Info("match.update published", "match_id", value.MatchID, "status", value.Status)
	return nil
}
