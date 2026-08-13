package mq

import (
	"fmt"

	amqp "github.com/rabbitmq/amqp091-go"
)

// RabbitMQConfig is the shared RabbitMQ connection and exchange configuration.
type RabbitMQConfig struct {
	URL          string `json:"url" mapstructure:"url"`
	ExchangeName string `json:"exchange_name" mapstructure:"exchange_name"`
}

// NewRabbitMQConnection establishes a RabbitMQ connection.
func NewRabbitMQConnection(config *RabbitMQConfig) (*amqp.Connection, error) {
	connection, err := amqp.Dial(config.URL)
	if err != nil {
		return nil, fmt.Errorf("connect RabbitMQ: %w", err)
	}
	return connection, nil
}
