package config

import (
	apolloconfig "github.com/apolloconfig/agollo/v5/env/config"
	"github.com/spf13/viper"
)

var v *viper.Viper

// AppConfig contains the local bootstrap settings required to start Apollo.
type AppConfig struct {
	Apollo *apolloconfig.AppConfig
}

type localAppConfig struct {
	Apollo struct {
		AppID             string `mapstructure:"app_id"`
		Cluster           string `mapstructure:"cluster"`
		Namespace         string `mapstructure:"namespace"`
		MetaAddr          string `mapstructure:"meta_addr"`
		Secret            string `mapstructure:"secret"`
		IsBackupConfig    bool   `mapstructure:"is_backup_config"`
		BackupConfigPath  string `mapstructure:"backup_config_path"`
		MustStart         bool   `mapstructure:"must_start"`
		SyncServerTimeout int    `mapstructure:"sync_server_timeout"`
	} `mapstructure:"apollo"`
}

// init creates an isolated Viper instance.
func init() {
	instance := viper.New()
	instance.AutomaticEnv()
	v = instance
}

// LoadLocalAppConfig reads the local Apollo bootstrap configuration.
func LoadLocalAppConfig(path string) (*AppConfig, error) {
	v.SetConfigFile(path)
	if err := v.ReadInConfig(); err != nil {
		return nil, err
	}
	var local localAppConfig
	if err := v.Unmarshal(&local); err != nil {
		return nil, err
	}
	return &AppConfig{Apollo: &apolloconfig.AppConfig{
		AppID:             local.Apollo.AppID,
		Cluster:           local.Apollo.Cluster,
		NamespaceName:     local.Apollo.Namespace,
		IP:                local.Apollo.MetaAddr,
		Secret:            local.Apollo.Secret,
		IsBackupConfig:    local.Apollo.IsBackupConfig,
		BackupConfigPath:  local.Apollo.BackupConfigPath,
		MustStart:         local.Apollo.MustStart,
		SyncServerTimeout: local.Apollo.SyncServerTimeout,
	}}, nil
}

// GenerateLocalAppConfig writes a runnable local bootstrap template.
func GenerateLocalAppConfig(path string) error {
	v.SetDefault("apollo.app_id", "server-allocator")
	v.SetDefault("apollo.cluster", "default")
	v.SetDefault("apollo.namespace", "cloudpvp.mq")
	v.SetDefault("apollo.meta_addr", "http://127.0.0.1:8080")
	v.SetDefault("apollo.secret", "")
	v.SetDefault("apollo.is_backup_config", false)
	v.SetDefault("apollo.backup_config_path", "")
	v.SetDefault("apollo.must_start", true)
	v.SetDefault("apollo.sync_server_timeout", 10)
	return v.WriteConfigAs(path)
}
