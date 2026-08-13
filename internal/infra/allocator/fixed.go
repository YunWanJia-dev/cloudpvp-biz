package allocator

import (
	"context"
	"fmt"
	"strings"

	"server-allocator/internal/domain/allocation"
	"server-allocator/internal/domain/match"
)

const fixedServerIP = "127.0.0.1"

// FixedAllocator attaches one fixed test server to every match.
type FixedAllocator struct{}

var _ allocation.Allocator = (*FixedAllocator)(nil)

// NewFixedAllocator creates the temporary fixed-IP allocator.
func NewFixedAllocator() *FixedAllocator {
	return &FixedAllocator{}
}

// Allocate ignores game mode and always attaches the fixed test IP.
func (a *FixedAllocator) Allocate(_ context.Context, value *match.Match) (*match.Server, error) {
	if value == nil || strings.TrimSpace(value.MatchID) == "" {
		return nil, fmt.Errorf("match_id is required")
	}
	return &match.Server{IP: fixedServerIP}, nil
}
