import { useCallback, useEffect, useRef, useState, type RefObject } from 'react';

/** Workspace padding (matches CanvasWorkspace inline style). */
const WORKSPACE_PADDING_PX = 48;
/** Horizontal ruler height included in the scaled canvas frame. */
const RULER_SIZE_PX = 20;

/**
 * Scale at which template height (+ ruler) fits the viewport — displayed as 100%.
 * fitZoom = (clientHeight - verticalChrome) / baseHeight
 */
function computeHeightFitZoom(
  workspaceEl: HTMLDivElement,
  baseHeight: number
): number | null {
  const availableHeight = workspaceEl.clientHeight - WORKSPACE_PADDING_PX * 2;
  if (availableHeight <= 0 || baseHeight <= 0) return null;
  const fit = (availableHeight - RULER_SIZE_PX) / baseHeight;
  return parseFloat(Math.max(0.05, Math.min(fit, 15)).toFixed(4));
}

export function useCanvasViewport(
  workspaceRef: RefObject<HTMLDivElement | null>,
  fabricCanvasRef: RefObject<any>,
  baseWidth: number,
  baseHeight: number
) {
  const [zoom, setZoomState] = useState(1);
  const [fitZoom, setFitZoomState] = useState(1);
  const [isSpacePressed, setIsSpacePressed] = useState(false);
  const fitZoomRef = useRef(1);
  const zoomRef = useRef(1);
  const isSpacePressedRef = useRef(false);
  const isDraggingViewportRef = useRef(false);
  const startDragCoordsRef = useRef({ x: 0, y: 0, scrollLeft: 0, scrollTop: 0 });
  /** When false, zoom tracks fitZoom on resize (stays at displayed 100%). */
  const hasUserAdjustedZoomRef = useRef(false);

  useEffect(() => {
    zoomRef.current = zoom;
  }, [zoom]);

  const syncFitZoom = useCallback(
    (options?: { forceToFit?: boolean; preserveRelative?: boolean }) => {
      const el = workspaceRef.current;
      if (!el) return;
      const nextFit = computeHeightFitZoom(el, baseHeight);
      if (nextFit == null) return;

      const prevFit = fitZoomRef.current;
      fitZoomRef.current = nextFit;
      setFitZoomState(nextFit);

      if (options?.forceToFit) {
        hasUserAdjustedZoomRef.current = false;
        setZoomState(nextFit);
        return;
      }

      if (!hasUserAdjustedZoomRef.current) {
        setZoomState(nextFit);
        return;
      }

      if (options?.preserveRelative && prevFit > 0) {
        const ratio = zoomRef.current / prevFit;
        setZoomState(parseFloat(Math.max(0.05, Math.min(ratio * nextFit, 15)).toFixed(4)));
      }
    },
    [workspaceRef, baseHeight]
  );

  const calculateFitZoom = useCallback(
    (force = false) => {
      syncFitZoom({ forceToFit: force });
    },
    [syncFitZoom]
  );

  const setZoom = useCallback((value: number | ((prev: number) => number)) => {
    hasUserAdjustedZoomRef.current = true;
    setZoomState((prev) => {
      const next = typeof value === 'function' ? value(prev) : value;
      return parseFloat(Math.max(0.05, Math.min(next, 15)).toFixed(4));
    });
  }, []);

  // Default: 100% = height fits container.
  useEffect(() => {
    syncFitZoom({ forceToFit: true });
  }, [syncFitZoom]);

  // Keep 100% aligned when the preview frame is resized.
  useEffect(() => {
    const el = workspaceRef.current;
    if (!el) return;
    const ro = new ResizeObserver(() => {
      syncFitZoom({ preserveRelative: true });
    });
    ro.observe(el);
    return () => ro.disconnect();
  }, [workspaceRef, syncFitZoom]);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.code !== 'Space') return;
      const activeEl = document.activeElement;
      const isTyping =
        activeEl?.tagName === 'INPUT' ||
        activeEl?.tagName === 'TEXTAREA' ||
        (activeEl as HTMLElement | null)?.isContentEditable ||
        fabricCanvasRef.current?.getActiveObject()?.isEditing;

      if (isTyping) return;

      e.preventDefault();
      if (!isSpacePressedRef.current) {
        isSpacePressedRef.current = true;
        setIsSpacePressed(true);
        if (workspaceRef.current) {
          workspaceRef.current.style.cursor = 'grab';
        }
      }
    };

    const handleKeyUp = (e: KeyboardEvent) => {
      if (e.code !== 'Space') return;
      isSpacePressedRef.current = false;
      setIsSpacePressed(false);
      if (workspaceRef.current) {
        workspaceRef.current.style.cursor = '';
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    window.addEventListener('keyup', handleKeyUp);
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
      window.removeEventListener('keyup', handleKeyUp);
    };
  }, [workspaceRef, fabricCanvasRef]);

  useEffect(() => {
    const canvasInstance = fabricCanvasRef.current;
    if (!canvasInstance) return;
    if (isSpacePressed) {
      canvasInstance.selection = false;
      canvasInstance.forEachObject((obj: any) => {
        obj.evented = false;
      });
    } else {
      canvasInstance.selection = true;
      canvasInstance.forEachObject((obj: any) => {
        const isLocked = obj.lockMovementX === true;
        obj.evented = !isLocked;
      });
    }
    canvasInstance.renderAll();
  }, [isSpacePressed, fabricCanvasRef]);

  const handleViewportMouseDown = (e: React.MouseEvent<HTMLDivElement>) => {
    if (!isSpacePressedRef.current || !workspaceRef.current) return;
    isDraggingViewportRef.current = true;
    workspaceRef.current.style.cursor = 'grabbing';
    startDragCoordsRef.current = {
      x: e.clientX,
      y: e.clientY,
      scrollLeft: workspaceRef.current.scrollLeft,
      scrollTop: workspaceRef.current.scrollTop,
    };
  };

  const handleViewportMouseMove = (e: React.MouseEvent<HTMLDivElement>) => {
    if (!isDraggingViewportRef.current || !workspaceRef.current) return;
    const dx = e.clientX - startDragCoordsRef.current.x;
    const dy = e.clientY - startDragCoordsRef.current.y;
    workspaceRef.current.scrollLeft = startDragCoordsRef.current.scrollLeft - dx;
    workspaceRef.current.scrollTop = startDragCoordsRef.current.scrollTop - dy;
  };

  const handleViewportMouseUp = () => {
    if (isDraggingViewportRef.current) {
      isDraggingViewportRef.current = false;
      if (workspaceRef.current) {
        workspaceRef.current.style.cursor = isSpacePressedRef.current ? 'grab' : '';
      }
    }
  };

  const handleZoom = (factor: number) => {
    setZoom((prev) => parseFloat((prev * factor).toFixed(4)));
  };

  const resetZoomTo100 = useCallback(() => {
    syncFitZoom({ forceToFit: true });
  }, [syncFitZoom]);

  const zoomPercent = fitZoom > 0 ? Math.round((zoom / fitZoom) * 100) : 100;
  const exceedsFitHeight = zoom > fitZoom + 0.0001;

  return {
    zoom,
    fitZoom,
    zoomPercent,
    exceedsFitHeight,
    setZoom,
    calculateFitZoom,
    resetZoomTo100,
    handleZoom,
    isSpacePressed,
    handleViewportMouseDown,
    handleViewportMouseMove,
    handleViewportMouseUp,
  };
}
