import React, { useState, useEffect } from 'react';
import ChatDisplay from './ChatDisplay';
import { TwitchEmbed } from './TwitchEmbed';
import { ChatService } from '../../services/ChatService';
import { ChatMessage } from '../../types';
const styles = require('../../styles/ChatView.module.css');

function ChatView() {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [connected, setConnected] = useState<boolean>(false);

  useEffect(() => {
    const chatService = new ChatService();
    
    chatService.connect(
      (message) => {
        setMessages(prev => [...prev.slice(-49), message]); // Keep last 50 messages
      },
      () => setConnected(true),
      () => setConnected(false)
    );

    return () => {
      chatService.disconnect();
    };
  }, []);

  return (
    <div className={styles.chatContainer}>
      <div className={styles.chatHeader}>
        <h2>Live Chat from FFT Battleground</h2>
        <div className={styles.chatSubtitle}>
          {connected ? '🟢 Connected' : '🔴 Disconnected'}
        </div>
      </div>
      
      <TwitchEmbed channel="fftbattleground" />
      <ChatDisplay messages={messages} connected={connected} />
    </div>
  );
}

export default ChatView;