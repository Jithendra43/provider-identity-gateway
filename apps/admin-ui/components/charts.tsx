'use client';

/**
 * Tiny dependency-free SVG chart primitives. Designed for the admin dashboard
 * where we have small fixed-size series (≤120 points) and don't want to pull
 * in recharts/chart.js.
 */

import { useMemo } from 'react';

type Point = { x: number; y: number };

function buildPath(points: Point[], width: number, height: number, padding = 4) {
  if (points.length === 0) return '';
  const xs = points.map((p) => p.x);
  const ys = points.map((p) => p.y);
  const xMin = Math.min(...xs);
  const xMax = Math.max(...xs);
  const yMin = 0;
  const yMax = Math.max(...ys, 1);
  const sx = (x: number) =>
    padding + ((x - xMin) / Math.max(1, xMax - xMin)) * (width - 2 * padding);
  const sy = (y: number) =>
    height - padding - ((y - yMin) / Math.max(0.001, yMax - yMin)) * (height - 2 * padding);
  return points.map((p, i) => `${i === 0 ? 'M' : 'L'} ${sx(p.x)} ${sy(p.y)}`).join(' ');
}

export function Sparkline({
  values,
  height = 48,
  color = '#0284c7',
  fill = 'rgba(2,132,199,0.12)',
  className,
}: {
  values: number[];
  height?: number;
  color?: string;
  fill?: string;
  className?: string;
}) {
  const width = 220;
  const padding = 4;
  const path = useMemo(
    () => buildPath(values.map((y, i) => ({ x: i, y })), width, height, padding),
    [values, height],
  );
  if (values.length < 2) {
    return (
      <div className={`flex h-[${height}px] items-center justify-center text-xs text-slate-400 ${className || ''}`}>
        Collecting samples…
      </div>
    );
  }
  // build closed area for fill
  const xs = values.map((_, i) => i);
  const xMin = Math.min(...xs);
  const xMax = Math.max(...xs);
  const sx = (x: number) =>
    padding + ((x - xMin) / Math.max(1, xMax - xMin)) * (width - 2 * padding);
  const areaPath = `${path} L ${sx(xMax)} ${height - padding} L ${sx(xMin)} ${height - padding} Z`;
  return (
    <svg
      viewBox={`0 0 ${width} ${height}`}
      preserveAspectRatio="none"
      className={`w-full ${className || ''}`}
      style={{ height }}
    >
      <path d={areaPath} fill={fill} />
      <path d={path} fill="none" stroke={color} strokeWidth={1.5} strokeLinejoin="round" />
    </svg>
  );
}

export function HBarChart({
  data,
  unit = 'ms',
  color = '#0284c7',
}: {
  data: { label: string; value: number }[];
  unit?: string;
  color?: string;
}) {
  const max = Math.max(...data.map((d) => d.value), 1);
  return (
    <div className="space-y-2">
      {data.map((d) => (
        <div key={d.label} className="flex items-center gap-3">
          <div className="w-24 truncate text-xs font-medium text-slate-600">{d.label}</div>
          <div className="relative flex-1">
            <div className="h-2 overflow-hidden rounded-full bg-slate-100">
              <div
                className="h-full rounded-full transition-all"
                style={{ width: `${(d.value / max) * 100}%`, backgroundColor: color }}
              />
            </div>
          </div>
          <div className="w-20 text-right text-xs font-mono tabular-nums text-slate-700">
            {d.value.toFixed(d.value < 10 ? 2 : 0)} {unit}
          </div>
        </div>
      ))}
    </div>
  );
}

export function Donut({
  segments,
  size = 120,
  thickness = 14,
}: {
  segments: { label: string; value: number; color: string }[];
  size?: number;
  thickness?: number;
}) {
  const total = segments.reduce((acc, s) => acc + s.value, 0);
  const radius = size / 2 - thickness / 2;
  const c = 2 * Math.PI * radius;
  let offset = 0;
  return (
    <div className="flex items-center gap-4">
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} className="-rotate-90">
        <circle cx={size / 2} cy={size / 2} r={radius} stroke="#f1f5f9" strokeWidth={thickness} fill="none" />
        {total > 0 && segments.map((s) => {
          const len = (s.value / total) * c;
          const dasharray = `${len} ${c}`;
          const el = (
            <circle
              key={s.label}
              cx={size / 2}
              cy={size / 2}
              r={radius}
              stroke={s.color}
              strokeWidth={thickness}
              fill="none"
              strokeDasharray={dasharray}
              strokeDashoffset={-offset}
              strokeLinecap="butt"
            />
          );
          offset += len;
          return el;
        })}
      </svg>
      <div className="space-y-1">
        {segments.map((s) => (
          <div key={s.label} className="flex items-center gap-2 text-xs">
            <span className="inline-block h-2.5 w-2.5 rounded-full" style={{ backgroundColor: s.color }} />
            <span className="text-slate-600">{s.label}</span>
            <span className="font-mono tabular-nums text-slate-900">{s.value}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
