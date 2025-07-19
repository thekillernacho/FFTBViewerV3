import React from 'react';
import TwitchEmbedVideo from 'react-twitch-embed-video';
const styles = require('./TwitchEmbed.module.css');

interface TwitchEmbedProps {
  channel?: string;
  width?: string;
  height?: string;
}

export const TwitchEmbed: React.FC<TwitchEmbedProps> = ({ 
  channel = 'fftbattleground',
  width = '100%',
  height = '280px'
}) => {
  return (
    <div className={styles.twitchEmbedContainer}>
      <h2 className={styles.title}>Live Stream</h2>
      <div className={styles.embedWrapper}>
        <TwitchEmbedVideo
          channel={channel}
          width={width}
          height={height}
          layout="video"
          theme="dark"
          autoplay={false}
          muted={true}
        />
      </div>
    </div>
  );
};