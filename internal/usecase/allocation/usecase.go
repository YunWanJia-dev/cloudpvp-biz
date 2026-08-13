package allocation

import (
	"context"
	"fmt"
	"strings"
	"time"

	domainallocation "server-allocator/internal/domain/allocation"
	"server-allocator/internal/domain/match"
)

// UseCase orchestrates server allocation and the complete match update.
type UseCase struct {
	allocator domainallocation.Allocator
	publisher domainallocation.Publisher
}

// NewUseCase creates the server allocation use case.
func NewUseCase(allocator domainallocation.Allocator, publisher domainallocation.Publisher) *UseCase {
	return &UseCase{allocator: allocator, publisher: publisher}
}

// Allocate validates match.create, allocates a server and publishes the complete updated match.
func (uc *UseCase) Allocate(ctx context.Context, value *match.Match) error {
	if err := value.ValidateCreate(); err != nil {
		return err
	}
	server, err := uc.allocator.Allocate(ctx, value)
	if err != nil {
		return fmt.Errorf("allocate server match_id=%s: %w", value.MatchID, err)
	}
	if server == nil || strings.TrimSpace(server.IP) == "" {
		return fmt.Errorf("allocate server match_id=%s: allocator returned empty server IP", value.MatchID)
	}

	// Build the update separately so a publication failure leaves the original create valid for retry classification.
	updated := *value
	updated.UpdatedAt = time.Now().UTC()
	updated.Status = match.MatchStatusInProgress
	updated.Server = &match.Server{IP: strings.TrimSpace(server.IP)}
	if err := uc.publisher.PublishMatchUpdate(ctx, &updated); err != nil {
		return fmt.Errorf("publish allocation result match_id=%s: %w", value.MatchID, err)
	}
	*value = updated
	return nil
}
