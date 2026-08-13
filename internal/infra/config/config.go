package config

import (
	"encoding/json"
	"fmt"
	"reflect"
	"strings"

	"server-allocator/internal/infra/mq"

	"github.com/apolloconfig/agollo/v5"
)

type apolloEntry struct {
	namespace string
	key       string
}

var typeNameMap = map[reflect.Type]apolloEntry{
	reflect.TypeOf(mq.RabbitMQConfig{}): {namespace: "cloudpvp.mq", key: "key"},
}

// Get reads a strongly typed value from Apollo.
func Get[T any](client agollo.Client) (*T, error) {
	targetType := reflect.TypeOf((*T)(nil)).Elem()
	entry, ok := typeNameMap[targetType]
	if !ok {
		return nil, fmt.Errorf("apollo config type not registered: %s", targetType)
	}
	value, err := client.GetConfigCache(entry.namespace).Get(entry.key)
	if err != nil {
		return nil, fmt.Errorf("apollo get %s/%s: %w", entry.namespace, entry.key, err)
	}
	data, err := configValueJSON(value)
	if err != nil {
		return nil, fmt.Errorf("apollo encode %s/%s: %w", entry.namespace, entry.key, err)
	}
	var result T
	if err := json.Unmarshal(data, &result); err != nil {
		return nil, err
	}
	return &result, nil
}

// configValueJSON normalizes Apollo values into JSON bytes.
func configValueJSON(value interface{}) ([]byte, error) {
	switch typed := value.(type) {
	case string:
		trimmed := strings.TrimSpace(typed)
		if trimmed == "" {
			return nil, fmt.Errorf("empty config value")
		}
		return []byte(trimmed), nil
	case []byte:
		return typed, nil
	default:
		return json.Marshal(value)
	}
}
