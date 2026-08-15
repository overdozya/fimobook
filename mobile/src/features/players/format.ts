import type { PlayerDetail, PlayerStatKey } from './types';

export const STAT_GROUPS: {
  title: string;
  stats: { key: PlayerStatKey; label: string }[];
}[] = [
  {
    title: '속도',
    stats: [
      { key: 'ACC', label: '가속' },
      { key: 'SPD', label: '질주 속도' },
    ],
  },
  {
    title: '슈팅',
    stats: [
      { key: 'FIN', label: '결정력' },
      { key: 'SHO', label: '슛 파워' },
      { key: 'LSA', label: '중거리 슛' },
      { key: 'VOL', label: '발리슛' },
    ],
  },
  {
    title: '패스',
    stats: [
      { key: 'SPA', label: '짧은 패스' },
      { key: 'LPA', label: '긴 패스' },
      { key: 'VIS', label: '시야' },
      { key: 'CRO', label: '크로스' },
    ],
  },
  {
    title: '드리블',
    stats: [
      { key: 'DRI', label: '드리블' },
      { key: 'BAC', label: '볼 컨트롤' },
      { key: 'AGI', label: '민첩성' },
      { key: 'REA', label: '반응 속도' },
      { key: 'BAL', label: '밸런스' },
    ],
  },
  {
    title: '수비',
    stats: [
      { key: 'MRK', label: '대인 수비' },
      { key: 'STT', label: '태클' },
      { key: 'SLT', label: '슬라이딩 태클' },
      { key: 'AWR', label: '수비 인식' },
      { key: 'HEA', label: '헤더' },
    ],
  },
  {
    title: '피지컬',
    stats: [
      { key: 'STR', label: '몸싸움' },
      { key: 'AGG', label: '적극성' },
      { key: 'JMP', label: '점프' },
      { key: 'STA', label: '스태미너' },
    ],
  },
];

export function formatPrice(price: number | null | undefined) {
  if (!price) return '가격 정보 없음';
  return `${price.toLocaleString('ko-KR')} MP`;
}

export function getPrice(player: PlayerDetail, level: number) {
  const value = player[`n8Price${level}`];
  return typeof value === 'number' ? value : 0;
}

export function getStat(player: PlayerDetail, key: PlayerStatKey) {
  const value = player[key];
  return typeof value === 'number' ? value : null;
}
