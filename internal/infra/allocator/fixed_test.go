package allocator

import (
	"context"
	"testing"

	"server-allocator/internal/domain/match"
)

// TestFixedAllocatorAlwaysReturnsFixedIP verifies the temporary allocator has no mode logic.
func TestFixedAllocatorAlwaysReturnsFixedIP(t *testing.T) {
	instance := NewFixedAllocator()
	value := &match.Match{MatchID: "match-1", GameMode: "any/mode"}
	first, err := instance.Allocate(context.Background(), value)
	if err != nil {
		t.Fatal(err)
	}
	if first.IP != "127.0.0.1" {
		t.Fatalf("server IP = %q", first.IP)
	}
}
