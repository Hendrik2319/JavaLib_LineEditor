package net.schwarzbaer.java.tools.lineeditor;

import java.awt.Component;
import java.awt.Graphics2D;

import javax.swing.JPopupMenu;

import net.schwarzbaer.java.lib.gui.ZoomableCanvas;

public interface EditorViewFeature
{
	public interface FeatureLineForm
	{
		void drawLines (Graphics2D g2, ZoomableCanvas.ViewState viewState);
		void drawPoints(Graphics2D g2, ZoomableCanvas.ViewState viewState);
	}
	
	void setEditorView(Component editorView);
	void draw(Graphics2D g2, int x, int y, int width, int height, ZoomableCanvas.ViewState viewState, Iterable<? extends FeatureLineForm> forms);
	void addToEditorViewContextMenu(JPopupMenu contextMenu);
	void prepareContextMenuToShow();
}
