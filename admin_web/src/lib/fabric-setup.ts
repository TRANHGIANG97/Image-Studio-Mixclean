import {
  FabricImage,
  FabricObject,
  Rect,
  Control,
  Point,
  controlsUtils,
} from 'fabric';

/** Side-effect: configure Fabric defaults and custom controls. Call once on editor load. */
export function initFabricEditorDefaults(): void {
  if (typeof window !== 'undefined' && FabricImage) {
    (FabricImage as any).ownDefaults = (FabricImage as any).ownDefaults || {};
    (FabricImage as any).ownDefaults.crossOrigin = 'anonymous';
  }

  FabricObject.ownDefaults.borderColor = '#6366f1';
  FabricObject.ownDefaults.borderScaleFactor = 2.5;
  FabricObject.ownDefaults.cornerColor = '#ffffff';
  FabricObject.ownDefaults.cornerStrokeColor = '#6366f1';
  FabricObject.ownDefaults.cornerSize = 32;
  FabricObject.ownDefaults.touchCornerSize = 48;
  FabricObject.ownDefaults.cornerStyle = 'circle';
  FabricObject.ownDefaults.transparentCorners = false;
  FabricObject.ownDefaults.borderOpacityWhenMoving = 0.8;

  const rxPositionHandler = (dim: any, finalMatrix: any, fabricObject: any) => {
    const x = -dim.x / 2 + (fabricObject.rx || 0);
    const y = -dim.y / 2 + (fabricObject.ry || 0);
    return new Point(x, y).transform(finalMatrix);
  };

  const rxActionHandler = (_eventData: any, transform: any, x: number, y: number) => {
    const { target } = transform;
    const localPoint = controlsUtils.getLocalPoint(transform, 'center', 'center', x, y);
    const maxRadius = Math.min(target.width, target.height) / 2;
    const newRx = Math.max(0, Math.min(maxRadius, target.width / 2 + localPoint.x));
    const newRy = Math.max(0, Math.min(maxRadius, target.height / 2 + localPoint.y));
    const radius = Math.max(newRx, newRy);
    if (target.rx !== radius || target.ry !== radius) {
      target.set({ rx: radius, ry: radius });
      return true;
    }
    return false;
  };

  const renderCornerRadiusControl = (
    ctx: CanvasRenderingContext2D,
    left: number,
    top: number,
    _styleOverride: unknown,
    _fabricObject: unknown
  ) => {
    ctx.save();
    ctx.beginPath();
    ctx.arc(left, top, 6, 0, 2 * Math.PI);
    ctx.fillStyle = '#ffffff';
    ctx.fill();
    ctx.lineWidth = 1.5;
    ctx.strokeStyle = '#6366f1';
    ctx.stroke();
    ctx.restore();
  };

  if (Rect?.prototype?.controls) {
    Rect.prototype.controls.cornerRadius = new Control({
      x: -0.5,
      y: -0.5,
      positionHandler: rxPositionHandler,
      actionHandler: rxActionHandler,
      cursorStyle: 'pointer',
      actionName: 'cornerRadius',
      render: renderCornerRadiusControl as any,
    });
  }
}
