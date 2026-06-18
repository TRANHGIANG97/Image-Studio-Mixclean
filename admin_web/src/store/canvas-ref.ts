/** Module-level canvas ref to avoid layers.store ↔ editor.store circular import. */
let editorCanvas: any | null = null;

export function setEditorCanvasRef(canvas: any | null): void {
  editorCanvas = canvas;
}

export function getEditorCanvasRef(): any | null {
  return editorCanvas;
}
