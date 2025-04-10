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
	"context"
	"log"
	"sync"
	"sync/atomic"
	"time"

	pb "github.com/eventbus/proto"
	"google.golang.org/grpc"
	"google.golang.org/grpc/connectivity"
	"google.golang.org/grpc/credentials/insecure"
)

const (
	// Reconnection parameters
	initialRetryDelay = 1 * time.Second
	maxRetryDelay     = 1 * time.Minute
	maxRetryAttempts  = 0 // 0 means infinite retries

	// Health check parameters
	healthCheckInterval = 10 * time.Second
	healthCheckTimeout  = 5 * time.Second

	// Event replication parameters
	eventBufferSize    = 1000
	replicationTimeout = 5 * time.Second
)

type PeerState int

const (
	PeerStateDisconnected PeerState = iota
	PeerStateConnecting
	PeerStateConnected
	PeerStateUnhealthy
)

type PeerManager struct {
	instanceID string
	peers      map[string]*peerConnection
	peersMu    sync.RWMutex
	ctx        context.Context
	cancel     context.CancelFunc
}

type peerConnection struct {
	address    string
	client     pb.EventBusClient
	conn       *grpc.ClientConn
	state      PeerState
	stateMu    sync.RWMutex
	eventQueue chan *pb.PeerMessage
	retryCount int
	lastRetry  time.Time
	metrics    PeerMetrics
}

func NewPeerManager(instanceID string) *PeerManager {
	ctx, cancel := context.WithCancel(context.Background())
	return &PeerManager{
		instanceID: instanceID,
		peers:      make(map[string]*peerConnection),
		ctx:        ctx,
		cancel:     cancel,
	}
}

func (pm *PeerManager) AddPeer(address string) {
	pm.peersMu.Lock()
	defer pm.peersMu.Unlock()

	if _, exists := pm.peers[address]; !exists {
		peer := &peerConnection{
			address:    address,
			state:      PeerStateDisconnected,
			eventQueue: make(chan *pb.PeerMessage, eventBufferSize),
			metrics: PeerMetrics{
				Address: address,
				State:   PeerStateDisconnected.String(),
			},
		}
		pm.peers[address] = peer

		// Start connection management goroutines
		go pm.managePeerConnection(peer)
		go pm.managePeerHealth(peer)
		go pm.manageEventReplication(peer)
	}
}

func (pm *PeerManager) managePeerConnection(peer *peerConnection) {
	retryDelay := initialRetryDelay

	for {
		select {
		case <-pm.ctx.Done():
			return
		default:
			if peer.getState() != PeerStateConnected {
				if err := pm.connectPeer(peer); err != nil {
					log.Printf("Failed to connect to peer %s: %v", peer.address, err)

					// Update metrics for reconnect attempts
					atomic.AddInt64(&peer.metrics.ReconnectAttempts, 1)

					// Exponential backoff
					time.Sleep(retryDelay)
					retryDelay *= 2
					if retryDelay > maxRetryDelay {
						retryDelay = maxRetryDelay
					}
					continue
				}

				// Reset retry delay on successful connection
				retryDelay = initialRetryDelay
			}
		}

		// Wait for disconnection before trying again
		for peer.getState() == PeerStateConnected {
			time.Sleep(healthCheckInterval)
		}
	}
}

func (pm *PeerManager) managePeerHealth(peer *peerConnection) {
	ticker := time.NewTicker(healthCheckInterval)
	defer ticker.Stop()

	for {
		select {
		case <-pm.ctx.Done():
			return
		case <-ticker.C:
			if peer.getState() == PeerStateConnected {
				if !pm.checkPeerHealth(peer) {
					peer.setState(PeerStateUnhealthy)
					log.Printf("Peer %s marked as unhealthy", peer.address)
				}
			}
		}
	}
}

func (pm *PeerManager) manageEventReplication(peer *peerConnection) {
	for {
		select {
		case <-pm.ctx.Done():
			return
		case event := <-peer.eventQueue:
			if peer.getState() == PeerStateConnected {
				ctx, cancel := context.WithTimeout(pm.ctx, replicationTimeout)
				_, err := peer.client.ReplicateEvent(ctx, event)
				cancel()

				if err != nil {
					log.Printf("Failed to replicate event to peer %s: %v", peer.address, err)
					// Increment dropped events metric
					atomic.AddInt64(&peer.metrics.EventsDropped, 1)

					// Re-queue the event if the peer is still connected
					if peer.getState() == PeerStateConnected {
						select {
						case peer.eventQueue <- event:
						default:
							log.Printf("Event queue full for peer %s, dropping event", peer.address)
							atomic.AddInt64(&peer.metrics.EventsDropped, 1)
						}
					}
				} else {
					// Increment sent events metric
					atomic.AddInt64(&peer.metrics.EventsSent, 1)
				}
			}
		}
	}
}

func (pm *PeerManager) connectPeer(peer *peerConnection) error {
	peer.setState(PeerStateConnecting)

	// Create connection options
	opts := []grpc.DialOption{
		grpc.WithTransportCredentials(insecure.NewCredentials()),
		grpc.WithBlock(),
		grpc.WithTimeout(healthCheckTimeout),
	}

	// Establish connection
	conn, err := grpc.Dial(peer.address, opts...)
	if err != nil {
		peer.setState(PeerStateDisconnected)
		return err
	}

	peer.conn = conn
	peer.client = pb.NewEventBusClient(conn)
	peer.setState(PeerStateConnected)
	log.Printf("Connected to peer %s", peer.address)

	return nil
}

func (pm *PeerManager) checkPeerHealth(peer *peerConnection) bool {
	if peer.conn == nil {
		return false
	}

	state := peer.conn.GetState()
	return state == connectivity.Ready || state == connectivity.Idle
}

func (pm *PeerManager) ReplicateEvent(event *pb.PeerMessage) {
	pm.peersMu.RLock()
	defer pm.peersMu.RUnlock()

	for _, peer := range pm.peers {
		if peer.getState() == PeerStateConnected {
			select {
			case peer.eventQueue <- event:
			default:
				log.Printf("Event queue full for peer %s, dropping event", peer.address)
			}
		}
	}
}

func (peer *peerConnection) setState(state PeerState) {
	peer.stateMu.Lock()
	defer peer.stateMu.Unlock()

	// Update metrics if connecting to Connected state
	if state == PeerStateConnected && peer.state != PeerStateConnected {
		peer.metrics.LastConnected = time.Now()
	}

	peer.state = state
	peer.metrics.State = state.String()
}

func (peer *peerConnection) getState() PeerState {
	peer.stateMu.RLock()
	defer peer.stateMu.RUnlock()
	return peer.state
}

// Updates the dynamic metrics for a peer
func (peer *peerConnection) updateMetrics() {
	// Update queue size
	peer.metrics.QueueSize = len(peer.eventQueue)

	// Update connected duration if currently connected
	if peer.state == PeerStateConnected && !peer.metrics.LastConnected.IsZero() {
		peer.metrics.ConnectedDuration = time.Since(peer.metrics.LastConnected)
	}
}

// GetMetrics returns metrics for all peers
func (pm *PeerManager) GetMetrics() []PeerMetrics {
	pm.peersMu.RLock()
	defer pm.peersMu.RUnlock()

	metrics := make([]PeerMetrics, 0, len(pm.peers))
	for _, peer := range pm.peers {
		peer.updateMetrics()
		metrics = append(metrics, peer.metrics)
	}
	return metrics
}

func (pm *PeerManager) Shutdown() {
	pm.cancel()
	pm.peersMu.Lock()
	defer pm.peersMu.Unlock()

	for _, peer := range pm.peers {
		if peer.conn != nil {
			peer.conn.Close()
		}
		close(peer.eventQueue)
	}
}
