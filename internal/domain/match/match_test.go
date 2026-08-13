package match

import "testing"

// TestMatchValidateCreate verifies the shared match.create contract.
func TestMatchValidateCreate(t *testing.T) {
	match := &Match{
		MatchID:  "match-1",
		GameMode: "matchmaker/5v5/competitive",
		Status:   MatchStatusWaitingForServer,
		Teams:    []Team{{LobbyIDs: []string{"1"}, Members: []Player{{PlayerID: "p1"}}}},
	}
	if err := match.ValidateCreate(); err != nil {
		t.Fatalf("ValidateCreate() error = %v", err)
	}
}

// TestMatchValidateCreateIgnoresGameMode verifies allocator has no mode-specific logic.
func TestMatchValidateCreateIgnoresGameMode(t *testing.T) {
	value := &Match{
		MatchID: "match-1",
		Status:  MatchStatusWaitingForServer,
	}
	if err := value.ValidateCreate(); err != nil {
		t.Fatalf("ValidateCreate() error = %v", err)
	}
}
