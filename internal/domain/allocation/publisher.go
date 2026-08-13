package allocation

import (
	"context"

	"server-allocator/internal/domain/match"
)

// Publisher publishes the complete match after allocation changes it.
type Publisher interface {
	PublishMatchUpdate(ctx context.Context, value *match.Match) error
}
