import { useCallback, useEffect, useRef, useState, type RefObject } from 'react';

export type CanvasGuide = { type: 'h' | 'v'; position: number };

const SNAP_TOLERANCE = 15;

export function useCanvasSnapping(
  baseWidth: number,
  baseHeight: number,
  guidesRef: RefObject<CanvasGuide[]>
) {
  const [snappingEnabled, setSnappingEnabled] = useState(true);
  const [guideX, setGuideX] = useState<number | null>(null);
  const [guideY, setGuideY] = useState<number | null>(null);
  const snappingEnabledRef = useRef(true);

  useEffect(() => {
    snappingEnabledRef.current = snappingEnabled;
  }, [snappingEnabled]);

  const clearGuides = useCallback(() => {
    setGuideX(null);
    setGuideY(null);
  }, []);

  const handleObjectMoving = useCallback(
    (e: any) => {
      if (!snappingEnabledRef.current) return;
      const obj = e.target;
      if (!obj) return;

      let snapX: number | null = null;
      let snapY: number | null = null;

      const objCenter = obj.getCenterPoint();
      const objWidth = obj.width * obj.scaleX;
      const objHeight = obj.height * obj.scaleY;

      const centerX = baseWidth / 2;
      const centerY = baseHeight / 2;

      if (Math.abs(objCenter.x - centerX) < SNAP_TOLERANCE) {
        snapX = centerX;
        obj.setPositionByOrigin({ x: centerX, y: objCenter.y }, 'center', 'center');
      }

      if (Math.abs(objCenter.y - centerY) < SNAP_TOLERANCE) {
        snapY = centerY;
        obj.setPositionByOrigin(
          { x: snapX !== null ? centerX : objCenter.x, y: centerY },
          'center',
          'center'
        );
      }

      const objLeft = objCenter.x - objWidth / 2;
      const objRight = objCenter.x + objWidth / 2;
      const objTop = objCenter.y - objHeight / 2;
      const objBottom = objCenter.y + objHeight / 2;

      if (snapX === null) {
        if (Math.abs(objLeft - 0) < SNAP_TOLERANCE) {
          snapX = objWidth / 2;
          obj.setPositionByOrigin({ x: snapX, y: objCenter.y }, 'center', 'center');
          snapX = 0;
        } else if (Math.abs(objRight - baseWidth) < SNAP_TOLERANCE) {
          snapX = baseWidth - objWidth / 2;
          obj.setPositionByOrigin({ x: snapX, y: objCenter.y }, 'center', 'center');
          snapX = baseWidth;
        }
      }

      if (snapY === null) {
        if (Math.abs(objTop - 0) < SNAP_TOLERANCE) {
          snapY = objHeight / 2;
          obj.setPositionByOrigin({ x: objCenter.x, y: snapY }, 'center', 'center');
          snapY = 0;
        } else if (Math.abs(objBottom - baseHeight) < SNAP_TOLERANCE) {
          snapY = baseHeight - objHeight / 2;
          obj.setPositionByOrigin({ x: objCenter.x, y: snapY }, 'center', 'center');
          snapY = baseHeight;
        }
      }

      const currentGuides = guidesRef.current || [];
      currentGuides.forEach((guide) => {
        const curCenter = obj.getCenterPoint();
        if (guide.type === 'v') {
          const curLeft = curCenter.x - objWidth / 2;
          const curRight = curCenter.x + objWidth / 2;

          if (Math.abs(curCenter.x - guide.position) < SNAP_TOLERANCE) {
            snapX = guide.position;
            obj.setPositionByOrigin({ x: guide.position, y: curCenter.y }, 'center', 'center');
          } else if (Math.abs(curLeft - guide.position) < SNAP_TOLERANCE) {
            snapX = guide.position;
            obj.setPositionByOrigin(
              { x: guide.position + objWidth / 2, y: curCenter.y },
              'center',
              'center'
            );
          } else if (Math.abs(curRight - guide.position) < SNAP_TOLERANCE) {
            snapX = guide.position;
            obj.setPositionByOrigin(
              { x: guide.position - objWidth / 2, y: curCenter.y },
              'center',
              'center'
            );
          }
        } else if (guide.type === 'h') {
          const curTop = curCenter.y - objHeight / 2;
          const curBottom = curCenter.y + objHeight / 2;

          if (Math.abs(curCenter.y - guide.position) < SNAP_TOLERANCE) {
            snapY = guide.position;
            obj.setPositionByOrigin({ x: curCenter.x, y: guide.position }, 'center', 'center');
          } else if (Math.abs(curTop - guide.position) < SNAP_TOLERANCE) {
            snapY = guide.position;
            obj.setPositionByOrigin(
              { x: curCenter.x, y: guide.position + objHeight / 2 },
              'center',
              'center'
            );
          } else if (Math.abs(curBottom - guide.position) < SNAP_TOLERANCE) {
            snapY = guide.position;
            obj.setPositionByOrigin(
              { x: curCenter.x, y: guide.position - objHeight / 2 },
              'center',
              'center'
            );
          }
        }
      });

      setGuideX(snapX);
      setGuideY(snapY);
    },
    [baseWidth, baseHeight, guidesRef]
  );

  return {
    snappingEnabled,
    setSnappingEnabled,
    guideX,
    guideY,
    handleObjectMoving,
    clearGuides,
  };
}
