import { useCallback, useEffect, useRef, useState, type RefObject } from 'react';

export function useCanvasViewport(
  workspaceRef: RefObject<HTMLDivElement | null>,
  fabricCanvasRef: RefObject<any>,
  baseWidth: number,
  baseHeight: number
) {
  const [zoom, setZoom] = useState(1);
  const [isSpacePressed, setIsSpacePressed] = useState(false);
  const isSpacePressedRef = useRef(false);
  const isDraggingViewportRef = useRef(false);
  const startDragCoordsRef = useRef({ x: 0, y: 0, scrollLeft: 0, scrollTop: 0 });

  const calculateFitZoom = useCallback(() => {
    if (!workspaceRef.current) return;
    const containerHeight = workspaceRef.current.clientHeight - 48;
    const containerWidth = workspaceRef.current.clientWidth - 48;
    if (containerHeight <= 0 || containerWidth <= 0) return;

    const fitZoomH = containerHeight / baseHeight;
    const fitZoomW = containerWidth / baseWidth;
    const fitZoom = Math.min(fitZoomH, fitZoomW);

    setZoom(parseFloat(Math.max(0.01, fitZoom).toFixed(2)));
  }, [workspaceRef, baseWidth, baseHeight]);

  useEffect(() => {
    if (!workspaceRef.current) return;
    const observer = new ResizeObserver(() => {
      calculateFitZoom();
    });
    observer.observe(workspaceRef.current);
    return () => observer.disconnect();
  }, [workspaceRef, calculateFitZoom]);

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
    setZoom((prev) => {
      const val = parseFloat((prev * factor).toFixed(2));
      return Math.max(0.1, Math.min(val, 3.0));
    });
  };

  return {
    zoom,
    setZoom,
    calculateFitZoom,
    handleZoom,
    isSpacePressed,
    handleViewportMouseDown,
    handleViewportMouseMove,
    handleViewportMouseUp,
  };
}
