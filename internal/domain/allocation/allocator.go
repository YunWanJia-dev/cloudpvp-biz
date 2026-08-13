package allocation

import (
	"context"

	"server-allocator/internal/domain/match"
)

// Allocator creates or retrieves the server assigned to one match.
type Allocator interface {
	Allocate(ctx context.Context, value *match.Match) (*match.Server, error)
}
