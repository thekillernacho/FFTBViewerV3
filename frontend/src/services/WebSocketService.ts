import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

export interface TrackEvent {
  songTitle: string;
  duration: number;
  username: string;
  time: string;
}

export interface ChatMessage {
  username: string;
  message: string;
  channel: string;
}

type WebSocketCallback<T> = (data: T) => void;

export class WebSocketService {
  private client: Client | null = null;
  private connected: boolean = false;
  private reconnectAttempts: number = 0;
  private maxReconnectAttempts: number = 5;
  private reconnectTimeout: number = 5000; // 5 seconds

  constructor() {
    this.connect();
  }

  private connect(): void {
    this.client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      connectHeaders: {},
      debug: (str) => {
        console.debug('WebSocket Debug:', str);
      },
      reconnectDelay: this.reconnectTimeout,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        console.log('WebSocket connected');
        this.connected = true;
        this.reconnectAttempts = 0;
      },
      onDisconnect: () => {
        console.log('WebSocket disconnected');
        this.connected = false;
        this.attemptReconnect();
      },
      onStompError: (frame) => {
        console.error('WebSocket STOMP error:', frame);
        this.connected = false;
      },
      onWebSocketError: (error) => {
        console.error('WebSocket error:', error);
        this.connected = false;
      }
    });

    this.client.activate();
  }

  private attemptReconnect(): void {
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++;
      console.log(`Attempting to reconnect (${this.reconnectAttempts}/${this.maxReconnectAttempts})...`);
      
      setTimeout(() => {
        if (!this.connected) {
          this.connect();
        }
      }, this.reconnectTimeout * this.reconnectAttempts);
    } else {
      console.error('Max reconnection attempts reached. Please refresh the page.');
    }
  }

  public subscribeToTracks(callback: WebSocketCallback<TrackEvent>): () => void {
    if (!this.client) {
      console.error('WebSocket client not initialized');
      return () => {};
    }

    // If not connected yet, wait for connection
    if (!this.connected) {
      console.warn('WebSocket not connected yet, waiting for connection...');
      const waitForConnection = () => {
        setTimeout(() => {
          if (this.connected && this.client) {
            this.performTrackSubscription(callback);
          } else if (this.client) {
            waitForConnection();
          }
        }, 100);
      };
      waitForConnection();
      return () => {}; // Return empty unsubscribe function for now
    }

    return this.performTrackSubscription(callback);
  }

  private performTrackSubscription(callback: WebSocketCallback<TrackEvent>): () => void {
    if (!this.client) {
      return () => {};
    }

    try {
      console.log('Subscribing to /topic/tracks...');
      const subscription = this.client.subscribe('/topic/tracks', (message: IMessage) => {
        try {
          console.log('Received track event:', message.body);
          const trackEvent: TrackEvent = JSON.parse(message.body);
          callback(trackEvent);
        } catch (error) {
          console.error('Error parsing track event:', error);
        }
      });

      console.log('Successfully subscribed to /topic/tracks');
      return () => {
        try {
          subscription.unsubscribe();
          console.log('Unsubscribed from /topic/tracks');
        } catch (error) {
          console.warn('Error unsubscribing from tracks:', error);
        }
      };
    } catch (error) {
      console.error('Error subscribing to tracks:', error);
      return () => {};
    }
  }

  public subscribeToMessages(callback: WebSocketCallback<ChatMessage>): () => void {
    if (!this.client) {
      console.error('WebSocket client not initialized');
      return () => {};
    }

    if (!this.connected) {
      console.warn('WebSocket not connected yet, subscription may fail');
    }

    try {
      const subscription = this.client.subscribe('/topic/messages', (message: IMessage) => {
        try {
          const chatMessage: ChatMessage = JSON.parse(message.body);
          callback(chatMessage);
        } catch (error) {
          console.error('Error parsing chat message:', error);
        }
      });

      return () => {
        try {
          subscription.unsubscribe();
        } catch (error) {
          console.warn('Error unsubscribing from messages:', error);
        }
      };
    } catch (error) {
      console.error('Error subscribing to messages:', error);
      return () => {};
    }
  }

  public isConnected(): boolean {
    return this.connected;
  }

  public disconnect(): void {
    if (this.client) {
      this.client.deactivate();
      this.client = null;
      this.connected = false;
    }
  }
}

// Singleton instance for reuse across components
export const webSocketService = new WebSocketService();