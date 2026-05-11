'use client';

import { CSSProperties, useCallback, useRef, useState } from 'react';

/**
 * 3D mouse-tracked tilt: returns handlers + inline style to apply on a card.
 * Pure CSS perspective transform, no deps.
 */
export function useTilt(maxDeg = 8) {
  const ref = useRef<HTMLDivElement | null>(null);
  const [style, setStyle] = useState<CSSProperties>({
    transform: 'perspective(900px) rotateX(0deg) rotateY(0deg) translateZ(0)',
    transition: 'transform 400ms cubic-bezier(0.22,1,0.36,1)',
  });

  const onMouseMove = useCallback(
    (e: React.MouseEvent<HTMLDivElement>) => {
      const el = ref.current;
      if (!el) return;
      const rect = el.getBoundingClientRect();
      const x = (e.clientX - rect.left) / rect.width;
      const y = (e.clientY - rect.top) / rect.height;
      const rotY = (x - 0.5) * (maxDeg * 2);
      const rotX = -(y - 0.5) * (maxDeg * 2);
      setStyle({
        transform: `perspective(900px) rotateX(${rotX.toFixed(2)}deg) rotateY(${rotY.toFixed(
          2,
        )}deg) translateZ(8px)`,
        transition: 'transform 80ms linear',
      });
    },
    [maxDeg],
  );

  const onMouseLeave = useCallback(() => {
    setStyle({
      transform: 'perspective(900px) rotateX(0deg) rotateY(0deg) translateZ(0)',
      transition: 'transform 500ms cubic-bezier(0.22,1,0.36,1)',
    });
  }, []);

  return { ref, style, onMouseMove, onMouseLeave };
}
