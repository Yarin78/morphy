import { IoFlash, IoMailOutline, IoTimeOutline } from 'react-icons/io5';
import 'flag-icons/css/flag-icons.min.css';
import chesscomLogo from '../assets/servers/chesscom.png';
import lichessLogo from '../assets/servers/lichess.png';
import playchessLogo from '../assets/servers/playchess.png';
import { eloTypeLabel, FIDE } from '../utils/eloType';
import type { EloTimeControl, EloTypeInfo } from '../utils/eloType';
import { nationInfo } from '../utils/nations';

const SERVER_LOGOS: Record<string, string> = {
  ChessBase: playchessLogo,
  'chess.com': chesscomLogo,
  lichess: lichessLogo,
};

/** A single bullet flying right, with two lines of speed behind it. */
const BulletIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg className={className} viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
    <path d="M9 8.5h6.5c3 0 5.5 1.6 7 3.5-1.5 1.9-4 3.5-7 3.5H9z" />
    <rect x="7" y="8.5" width="1.5" height="7" rx="0.4" />
    <rect x="1" y="9.5" width="4.5" height="1.3" rx="0.65" />
    <rect x="2.5" y="13.2" width="3" height="1.3" rx="0.65" />
  </svg>
);

const TIME_CONTROL_ICONS: Partial<Record<EloTimeControl, React.ComponentType<{ className?: string }>>> = {
  BULLET: BulletIcon,
  BLITZ: IoFlash,
  RAPID: IoTimeOutline,
  CORRESPONDENCE: IoMailOutline,
};

/** A server's logo, or its name when there's no logo for it. */
export const ServerLogo: React.FC<{ name: string }> = ({ name }) =>
  SERVER_LOGOS[name] ? (
    <img className="elo-server-logo" src={SERVER_LOGOS[name]} alt={name} title={name} />
  ) : (
    <span className="elo-type-text">{name}</span>
  );

/** A nation's flag; an unknown flag for no nation, or one without a flag. */
export const NationFlag: React.FC<{
  nation: string | undefined;
  /** What the flag stands for, if not the nation: a language, say. */
  label?: string;
}> = ({ nation, label }) => {
  const info = nationInfo(nation);
  const name = label ?? info?.name ?? 'Unknown nation';
  if (info?.flagImage) {
    return <img className="elo-flag" src={info.flagImage} alt={name} title={name} />;
  }
  if (info?.flag) {
    return <span className={`fi fi-${info.flag} elo-flag`} role="img" aria-label={name} title={name} />;
  }
  return (
    <span className="elo-flag elo-flag-unknown" role="img" aria-label={name} title={name}>
      ?
    </span>
  );
};

/** A time control's icon; normal has none. */
export const TimeControlIcon: React.FC<{ timeControl: EloTimeControl }> = ({ timeControl }) => {
  const Icon = TIME_CONTROL_ICONS[timeControl];
  return Icon ? <Icon className="elo-time-control-icon" /> : null;
};

/**
 * A rating type as icons: the server's logo, the nation's flag, or FIDE/ICCF as text, followed by
 * the time control's icon. ICCF has none, as it's correspondence by definition.
 */
export const EloTypeIcons: React.FC<{ type: EloTypeInfo | null }> = ({ type }) => {
  const t = type ?? FIDE;
  const what =
    t.kind === 'SERVER' ? (
      <ServerLogo name={t.name ?? ''} />
    ) : t.kind === 'NATIONAL' ? (
      <NationFlag nation={t.nation} />
    ) : (
      <span className="elo-type-text">{t.name || 'FIDE'}</span>
    );
  const iccf = t.kind === 'INTERNATIONAL' && t.timeControl === 'CORRESPONDENCE';
  return (
    <span className="elo-type-icons" title={eloTypeLabel(type)}>
      {what}
      {!iccf && <TimeControlIcon timeControl={t.timeControl} />}
    </span>
  );
};
