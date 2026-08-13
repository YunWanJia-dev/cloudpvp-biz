package allocation

import (
	"context"
	"errors"
	"reflect"
	"testing"
	"time"

	"server-allocator/internal/domain/match"
)

type testAllocator struct{ server *match.Server }

// Allocate returns the configured test server.
func (a testAllocator) Allocate(context.Context, *match.Match) (*match.Server, error) {
	return a.server, nil
}

type testPublisher struct {
	value *match.Match
	err   error
}

// PublishMatchUpdate records the complete updated match.
func (p *testPublisher) PublishMatchUpdate(_ context.Context, value *match.Match) error {
	p.value = value
	return p.err
}

// TestAllocateKeepsCreateValidWhenPublicationFails verifies a broker failure can safely requeue the original create.
func TestAllocateKeepsCreateValidWhenPublicationFails(t *testing.T) {
	publisher := &testPublisher{err: errors.New("broker unavailable")}
	uc := NewUseCase(testAllocator{server: &match.Server{IP: "127.0.0.1"}}, publisher)
	createdAt := time.Date(2026, time.August, 13, 8, 0, 0, 0, time.UTC)
	value := &match.Match{
		MatchID:   "match-retry",
		GameMode:  "matchmaker/5v5/competitive",
		Status:    match.MatchStatusWaitingForServer,
		Teams:     []match.Team{{LobbyIDs: []string{"1"}, Members: []match.Player{{PlayerID: "p1"}}}},
		CreatedAt: createdAt,
		UpdatedAt: createdAt,
	}

	if err := uc.Allocate(context.Background(), value); err == nil {
		t.Fatal("Allocate() error = nil")
	}
	if err := value.ValidateCreate(); err != nil {
		t.Fatalf("original create became invalid after publication failure: %v", err)
	}
	if value.Status != match.MatchStatusWaitingForServer || value.Server != nil || !value.UpdatedAt.Equal(createdAt) {
		t.Fatalf("original create was mutated = %#v", value)
	}
	if publisher.value == nil || publisher.value.Status != match.MatchStatusInProgress {
		t.Fatalf("attempted update = %#v", publisher.value)
	}
	if publisher.value.Server == nil || publisher.value.Server.IP != "127.0.0.1" {
		t.Fatalf("attempted update server = %#v", publisher.value.Server)
	}
	if publisher.value.MatchID != value.MatchID || publisher.value.GameMode != value.GameMode ||
		!reflect.DeepEqual(publisher.value.Teams, value.Teams) || !publisher.value.CreatedAt.Equal(value.CreatedAt) {
		t.Fatalf("attempted update did not preserve the complete match: %#v", publisher.value)
	}
}

// TestAllocateRejectsEmptyServer verifies an invalid allocation never publishes IN_PROGRESS.
func TestAllocateRejectsEmptyServer(t *testing.T) {
	testCases := []struct {
		name   string
		server *match.Server
	}{
		{name: "nil server", server: nil},
		{name: "empty IP", server: &match.Server{}},
		{name: "blank IP", server: &match.Server{IP: "   "}},
	}

	for _, testCase := range testCases {
		t.Run(testCase.name, func(t *testing.T) {
			publisher := &testPublisher{}
			uc := NewUseCase(testAllocator{server: testCase.server}, publisher)
			value := &match.Match{MatchID: "match-invalid-server", Status: match.MatchStatusWaitingForServer}
			if err := uc.Allocate(context.Background(), value); err == nil {
				t.Fatal("Allocate() error = nil")
			}
			if publisher.value != nil {
				t.Fatalf("invalid server was published = %#v", publisher.value)
			}
			if err := value.ValidateCreate(); err != nil {
				t.Fatalf("original create became invalid: %v", err)
			}
		})
	}
}

// TestAllocatePublishesInProgressMatch verifies create is converted into a complete IN_PROGRESS update.
func TestAllocatePublishesInProgressMatch(t *testing.T) {
	publisher := &testPublisher{}
	uc := NewUseCase(testAllocator{server: &match.Server{IP: "127.0.0.1"}}, publisher)
	createdAt := time.Date(2026, time.August, 13, 8, 0, 0, 0, time.UTC)
	teams := []match.Team{{LobbyIDs: []string{"1"}, Members: []match.Player{{PlayerID: "p1"}}}}
	value := &match.Match{
		MatchID:   "match-1",
		GameMode:  "matchmaker/5v5/competitive",
		Status:    match.MatchStatusWaitingForServer,
		Teams:     teams,
		CreatedAt: createdAt,
		UpdatedAt: createdAt,
	}
	if err := uc.Allocate(context.Background(), value); err != nil {
		t.Fatalf("Allocate() error = %v", err)
	}
	if publisher.value == nil {
		t.Fatal("publisher did not receive the complete updated match object")
	}
	if publisher.value.Status != match.MatchStatusInProgress || publisher.value.Server == nil {
		t.Fatalf("published match = %#v", publisher.value)
	}
	if publisher.value.Server.IP != "127.0.0.1" {
		t.Fatalf("published server IP = %q", publisher.value.Server.IP)
	}
	if publisher.value.MatchID != "match-1" || publisher.value.GameMode != "matchmaker/5v5/competitive" {
		t.Fatalf("published identity changed = %#v", publisher.value)
	}
	if !reflect.DeepEqual(publisher.value.Teams, teams) {
		t.Fatalf("published teams = %#v, want %#v", publisher.value.Teams, teams)
	}
	if !publisher.value.CreatedAt.Equal(createdAt) {
		t.Fatalf("published created_at = %s", publisher.value.CreatedAt)
	}
	if !publisher.value.UpdatedAt.After(createdAt) {
		t.Fatalf("published updated_at = %s, want after %s", publisher.value.UpdatedAt, createdAt)
	}
	if !reflect.DeepEqual(value, publisher.value) {
		t.Fatalf("successful allocation did not apply the published update: value=%#v published=%#v", value, publisher.value)
	}
}

// TestAllocatePublishesEveryValidCreate verifies all valid create messages follow the same complete update flow.
func TestAllocatePublishesEveryValidCreate(t *testing.T) {
	testCases := []struct {
		name  string
		value *match.Match
	}{
		{
			name: "complete teams",
			value: &match.Match{
				MatchID:  "match-complete",
				GameMode: "matchmaker/5v5/competitive",
				Status:   match.MatchStatusWaitingForServer,
				Teams: []match.Team{
					{LobbyIDs: []string{"1"}, Members: []match.Player{{PlayerID: "p1"}}},
					{LobbyIDs: []string{"2"}, Members: []match.Player{{PlayerID: "p2"}}},
				},
			},
		},
		{
			name: "mode agnostic minimum",
			value: &match.Match{
				MatchID: "match-minimum",
				Status:  match.MatchStatusWaitingForServer,
			},
		},
	}

	for _, testCase := range testCases {
		t.Run(testCase.name, func(t *testing.T) {
			publisher := &testPublisher{}
			uc := NewUseCase(testAllocator{server: &match.Server{IP: "127.0.0.1"}}, publisher)
			if err := uc.Allocate(context.Background(), testCase.value); err != nil {
				t.Fatalf("Allocate() error = %v", err)
			}
			if publisher.value == nil {
				t.Fatal("publisher did not receive the complete updated match")
			}
			if publisher.value.Status != match.MatchStatusInProgress {
				t.Fatalf("published status = %q", publisher.value.Status)
			}
			if publisher.value.Server == nil || publisher.value.Server.IP != "127.0.0.1" {
				t.Fatalf("published server = %#v", publisher.value.Server)
			}
			if !reflect.DeepEqual(testCase.value, publisher.value) {
				t.Fatalf("successful allocation did not apply the published update: value=%#v published=%#v", testCase.value, publisher.value)
			}
		})
	}
}
