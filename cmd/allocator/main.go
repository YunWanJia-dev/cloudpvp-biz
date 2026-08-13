package main

import (
	"context"
	"errors"
	"flag"

	"server-allocator/internal/app"
)

// main parses startup arguments and starts the server allocator.
func main() {
	configPath := flag.String("config", "", "path to local Apollo bootstrap config")
	flag.Parse()
	if err := app.Run(context.Background(), app.Options{ConfigPath: *configPath}); err != nil && !errors.Is(err, context.Canceled) {
		panic(err)
	}
}
