// Copyright (c) 2025 Chris Collins chris@hitorro.com
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.

package service

import (
	"encoding/json"
	"time"
)

// PeerState string representation
func (p PeerState) String() string {
	switch p {
	case PeerStateDisconnected:
		return "Disconnected"
	case PeerStateConnecting:
		return "Connecting"
	case PeerStateConnected:
		return "Connected"
	case PeerStateUnhealthy:
		return "Unhealthy"
	default:
		return "Unknown"
	}
}

// PeerMetrics stores metrics information for a peer connection
type PeerMetrics struct {
	Address           string        `json:"address"`
	State             string        `json:"state"`
	ConnectedDuration time.Duration `json:"connected_duration,omitempty"`
	LastConnected     time.Time     `json:"last_connected,omitempty"`
	ReconnectAttempts int64         `json:"reconnect_attempts"`
	EventsSent        int64         `json:"events_sent"`
	EventsDropped     int64         `json:"events_dropped"`
	QueueSize         int           `json:"queue_size"`
}

// ServiceMetrics stores metrics information for the service
type ServiceMetrics struct {
	InstanceID      string        `json:"instance_id"`
	Uptime          time.Duration `json:"uptime"`
	StartTime       time.Time     `json:"start_time"`
	SubscriberCount int           `json:"subscriber_count"`
	ProcessedEvents int64         `json:"processed_events"`
	Peers           []PeerMetrics `json:"peers"`
}

// ToJSON converts metrics to JSON string
func (m ServiceMetrics) ToJSON() (string, error) {
	data, err := json.MarshalIndent(m, "", "  ")
	if err != nil {
		return "", err
	}
	return string(data), nil
}
