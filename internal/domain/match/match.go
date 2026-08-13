package match

import (
	"fmt"
	"strings"
	"time"
)

// MatchStatus describes the allocation state carried by the complete match object.
type MatchStatus string

const (
	// MatchStatusWaitingForServer means matchmaking completed and is waiting for allocation.
	MatchStatusWaitingForServer MatchStatus = "WAITING_FOR_SERVER"
	// MatchStatusInProgress means a server has been attached and the match can start.
	MatchStatusInProgress MatchStatus = "IN_PROGRESS"
)

// Player is a player contained in a match team.
type Player struct {
	PlayerID string `json:"player_id"`
}

// Team is one side of a match and preserves its source lobby grouping.
type Team struct {
	LobbyIDs []string `json:"lobby_ids"`
	Members  []Player `json:"members"`
}

// Server contains the server allocation attached to a match update.
type Server struct {
	IP string `json:"ip"`
}

// Match is the complete lifecycle object exchanged by matcher, allocator and biz.
type Match struct {
	MatchID   string      `json:"match_id"`
	GameMode  string      `json:"game_mode"`
	Status    MatchStatus `json:"status"`
	Teams     []Team      `json:"teams"`
	Server    *Server     `json:"server"`
	CreatedAt time.Time   `json:"created_at"`
	UpdatedAt time.Time   `json:"updated_at"`
}

// ValidateCreate verifies the minimum identity and lifecycle fields required for an update.
func (m *Match) ValidateCreate() error {
	if m == nil {
		return fmt.Errorf("match is nil")
	}
	if strings.TrimSpace(m.MatchID) == "" {
		return fmt.Errorf("match_id is required")
	}
	if m.Status != MatchStatusWaitingForServer {
		return fmt.Errorf("unsupported create status=%s", m.Status)
	}
	if m.Server != nil {
		return fmt.Errorf("server must be empty for match.create")
	}
	return nil
}
