package config

import (
	"log"

	"github.com/apolloconfig/agollo/v5"
	apolloconfig "github.com/apolloconfig/agollo/v5/env/config"
)

// NewApolloClient starts an Apollo client from the local bootstrap configuration.
func NewApolloClient(value *apolloconfig.AppConfig) agollo.Client {
	client, err := agollo.StartWithConfig(func() (*apolloconfig.AppConfig, error) { return value, nil })
	if err != nil {
		log.Panicf("start agollo error: %s", err)
	}
	return client
}
