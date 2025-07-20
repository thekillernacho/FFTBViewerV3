import React from 'react';
const styles = require('../../styles/ChatView.module.css');

interface ConnectionStatusProps {
  connected: boolean;
}

function ConnectionStatus({ connected }: ConnectionStatusProps) {
  return (
    <div className={styles.loading}>
      {connected ? 'Waiting for messages...' : 'Connecting to chat...'}
    </div>
  );
}

export default ConnectionStatus;