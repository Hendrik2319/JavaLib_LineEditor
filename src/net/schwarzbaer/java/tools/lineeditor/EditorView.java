package net.schwarzbaer.java.tools.lineeditor;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Vector;
import java.util.function.BiConsumer;

import javax.swing.JPanel;

import net.schwarzbaer.java.lib.gui.ZoomableCanvas;
import net.schwarzbaer.java.lib.image.linegeometry.Math2;
import net.schwarzbaer.java.tools.lineeditor.EditorView.GuideLine.Type;

class EditorView extends ZoomableCanvas<EditorView.ViewState> {
	
	static final int MAX_NEAR_DISTANCE = 20;
	static final int MAX_GUIDELINE_DISTANCE = 3;

	private static final long serialVersionUID = -2936567438026759797L;
	
	private static final Color COLOR_AXIS       = new Color(0x70000000,true);
	private static final Color COLOR_BACKGROUND = Color.WHITE;
	private static final Color COLOR_GUIDELINES             = new Color(0x2f7f7f7f,true);
	private static final Color COLOR_GUIDELINES_HIGHLIGHTED = new Color(0xb5f0b5);
	private static final Color COLOR_POINT_FILL             = Color.WHITE;
	private static final Color COLOR_POINT_FILL_HIGHLIGHTED = Color.GREEN;
	private static final Color COLOR_POINT_CONTOUR          = Color.BLACK;

	private LineForm<?>[] forms = null;
	private Vector<GuideLine> guideLines = null;
	private final HashSet<LineForm<?>> highlightedForms = new HashSet<>();
	private LineFormEditing<?> formEditing = null;
	private GuideLine highlightedGuideLine = null;
	private final Context context;
	private boolean stickToGuideLines = true;
	private boolean stickToFormPoints = true;
	private final EditorViewFeature[] features;
	
	EditorView(EditorViewFeature[] features, Context context) {
		this.features = features;
		this.context = context;
		Debug.Assert(this.context!=null);
		
		for (EditorViewFeature feature : features)
			feature.setEditorView(this);
		
		activateMapScale(COLOR_AXIS, "px");
		activateAxes(COLOR_AXIS, true,true,true,true);
		addKeyListener(new KeyListener() {
			@Override public void keyTyped   (KeyEvent e) { if (formEditing!=null) formEditing.keyTyped   (e); }
			@Override public void keyReleased(KeyEvent e) { if (formEditing!=null) formEditing.keyReleased(e); }
			@Override public void keyPressed (KeyEvent e) { if (formEditing!=null) formEditing.keyPressed (e); }
		});
	}

	boolean isStickToGuideLines() { return stickToGuideLines; }
	boolean isStickToFormPoints() { return stickToFormPoints; }
	void setStickToGuideLines(boolean stickToGuideLines) { this.stickToGuideLines = stickToGuideLines; repaint(); }
	void setStickToFormPoints(boolean stickToFormPoints) { this.stickToFormPoints = stickToFormPoints; repaint(); }

	void setGuideLines(Vector<GuideLine> guideLines) {
		this.guideLines = guideLines;
		repaint();
	}

	void setForms(LineForm<?>[] forms) {
		this.forms = forms;
		highlightedForms.clear();
		deselect();
		repaint();
	}

	ViewState getViewState() { return viewState; }
	
	interface Context {
		void setValuePanel(JPanel panel);
		void updateHighlightedForms(HashSet<LineForm<?>> forms);
		void showsContextMenu(int x, int y);
	}
	
	Point2D.Double stickToGuides_px(int xs, int ys, boolean isXFixed, boolean isYFixed) {
		double x = viewState.convertPos_ScreenToAngle_LongX(xs);
		double y = viewState.convertPos_ScreenToAngle_LatY (ys);
		return stickToGuides(x, y, isXFixed, isYFixed);
	}
	Point2D.Double stickToGuides(double x, double y, boolean isXFixed, boolean isYFixed) {
		if (!isXFixed || !isYFixed) {
			double maxDist = viewState.convertLength_ScreenToLength(MAX_GUIDELINE_DISTANCE);
			GuideResult resultX = !stickToGuideLines ? null : GuideLine.stickToGuideLines(x, Type.Vertical  , maxDist, guideLines);
			GuideResult resultY = !stickToGuideLines ? null : GuideLine.stickToGuideLines(y, Type.Horizontal, maxDist, guideLines);
			GuideResult resultP = !stickToFormPoints ? null : stickToFormPoints(x,y,maxDist);
			//System.out.printf(Locale.ENGLISH, "stickToGuides( x:%1.3f%s, y:%1.3f%s) -> GlX:%s GlY:%s FP:%s%n", x,isXFixed?"[F]":"", y,isYFixed?"[F]":"", resultX, resultY, resultP );
			
			if (!isXFixed && !isYFixed &&
				resultP!=null && resultP.x!=null && resultP.y!=null &&
				(resultX==null || resultP.dist<resultX.dist) &&
				(resultY==null || resultP.dist<resultY.dist))
				return new Point2D.Double(resultP.x, resultP.y);
			if (!isXFixed && resultX!=null && resultX.x!=null) x = resultX.x;
			if (!isYFixed && resultY!=null && resultY.y!=null) y = resultY.y;
		}
		return new Point2D.Double(x,y);
	}


	private GuideResult stickToFormPoints(double x, double y, double maxDist) {
		if (forms==null) return null;
		TempGuideResult result = new TempGuideResult();
		for (LineForm<?> form:forms) {
			if (formEditing!=null && form==formEditing.getForm()) continue;
			form.forEachPoint((xP,yP)->{
				double d = Math2.dist(xP,yP,x,y);
				if (d<maxDist && (result.dist==null || d<result.dist)) {
					result.dist = d;
					result.x = xP;
					result.y = yP;
				}
			});
		}
		return result.toGuideResult();
	}



//	float stickToGuideLineX(float x) {
//		GuideResult result = GuideLine.stickToGuideLines(x, Type.Vertical  , viewState.convertLength_ScreenToLength(MAX_GUIDELINE_DISTANCE), guideLines);
//		if (result!=null && result.x!=null) x=result.x;
//		return x;
//	}
//	float stickToGuideLineY(float y) {
//		GuideResult result = GuideLine.stickToGuideLines(y, Type.Horizontal, viewState.convertLength_ScreenToLength(MAX_GUIDELINE_DISTANCE), guideLines);
//		if (result!=null && result.y!=null) y=result.y;
//		return y;
//	}
	
	void forEachGuideLines(BiConsumer<GuideLine.Type,Double> action) {
		for (GuideLine gl:guideLines)
			action.accept(gl.type,gl.pos);
	}

	@Override public void mouseClicked (MouseEvent e) {
		switch (e.getButton()) {
		case MouseEvent.BUTTON1:
			if (formEditing!=null) {
				if (!formEditing.onClicked(e)) deselect();
			} else
				setSelectedForm(e);
			break;
		case MouseEvent.BUTTON3:
			context.showsContextMenu(e.getX(),e.getY());
			break;
		}
	}
	@Override public void mouseEntered (MouseEvent e) { if (formEditing!=null) formEditing.onEntered (e); else setHighlightedForm(e.getPoint()); setHighlightedGuideLine(null); }
	@Override public void mouseMoved   (MouseEvent e) { if (formEditing!=null) formEditing.onMoved   (e); else setHighlightedForm(e.getPoint()); }
	@Override public void mouseExited  (MouseEvent e) { if (formEditing!=null) formEditing.onExited  (e); else setHighlightedForm((Point)null ); }
	@Override public void mousePressed (MouseEvent e) { if (formEditing==null || !formEditing.onPressed (e)) super.mousePressed (e); }
	@Override public void mouseReleased(MouseEvent e) { if (formEditing==null || !formEditing.onReleased(e)) super.mouseReleased(e); }
	@Override public void mouseDragged (MouseEvent e) { if (formEditing==null || !formEditing.onDragged (e)) super.mouseDragged (e); }
	
	private void deselect() {
		if (formEditing!=null) formEditing.stopEditing();
		formEditing=null;
		context.setValuePanel(null);
	}
	
	private void setSelectedForm(MouseEvent e) {
		setSelectedForm(getNext(e.getPoint()), e);
	}
	void setSelectedForm(LineForm<?> selectedForm) {
		setSelectedForm(selectedForm, null);
	}
	private void setSelectedForm(LineForm<?> selectedForm, MouseEvent e) {
		if (formEditing!=null) formEditing.stopEditing();
		formEditing = LineFormEditing.create(selectedForm,viewState,this,e);
		if (formEditing!=null) context.setValuePanel(formEditing.createValuePanel());
		highlightedForms.clear();
		repaint();
	}

	void setHighlightedGuideLine(GuideLine highlightedGuideLine) {
		this.highlightedGuideLine = highlightedGuideLine;
		repaint();
	}

	private void setHighlightedForm(Point p) {
		Vector<LineForm<?>> vector = new Vector<>();
		LineForm<?> form = getNext(p);
		if (form!=null) { vector.add(form); }
		setHighlightedForms(vector,true);
	}
	void setHighlightedForms(List<LineForm<?>> forms) {
		setHighlightedForms(forms,false);
	}
	private void setHighlightedForms(List<LineForm<?>> highlightedForms, boolean updateHighlightedInFormList) {
		this.highlightedForms.clear();
		if (highlightedForms!=null && !highlightedForms.isEmpty())
			this.highlightedForms.addAll(highlightedForms);
		repaint();
		if (updateHighlightedInFormList)
			context.updateHighlightedForms(this.highlightedForms);
	}

	private LineForm<?> getNext(Point p) {
		if (p==null || forms==null) return null;
		
		Double minDist = null;
		LineForm<?> nearest = null;
		double maxDist = viewState.convertLength_ScreenToLength(MAX_NEAR_DISTANCE);
		double x = viewState.convertPos_ScreenToAngle_LongX(p.x);
		double y = viewState.convertPos_ScreenToAngle_LatY (p.y);
		//System.out.printf(Locale.ENGLISH, "getNext: %f,%f (max:%f)%n", x,y,maxDist);
		for (LineForm<?> form:forms) {
			Double dist = form.getDistance(x,y,maxDist);
			//System.out.printf(Locale.ENGLISH, "Distance[%s]: %s%n", form.getClass().getSimpleName(), dist);
			if (dist!=null && (minDist==null || minDist>dist)) {
				minDist = dist;
				nearest = form;
			}
		}
		
		return nearest;
	}

	Rectangle2D.Float getViewRectangle() {
		Rectangle2D.Float rect = new Rectangle2D.Float();
		rect.x      = (float) viewState.convertPos_ScreenToAngle_LongX(0);
		rect.y      = (float) viewState.convertPos_ScreenToAngle_LatY (0);
		rect.width  = (float) (viewState.convertPos_ScreenToAngle_LongX(0+this.width)  - rect.x);
		rect.height = (float) (viewState.convertPos_ScreenToAngle_LatY (0+this.height) - rect.y);
		return rect;
	}

	@Override
	protected void paintCanvas(Graphics g, int x, int y, int width, int height) {
		g.setColor(COLOR_BACKGROUND);
		g.fillRect(x, y, width, height);
		
		if (g instanceof Graphics2D) {
			Graphics2D g2 = (Graphics2D) g;
			g2.setClip(x, y, width, height);
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			//g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			
			if (forms!=null)
				for (EditorViewFeature feature : features)
					feature.draw(g2, x, y, width, height, viewState, Arrays.asList(forms));
			
			if (guideLines!=null)
				for (GuideLine gl:guideLines) {
					g2.setColor(gl==highlightedGuideLine ? COLOR_GUIDELINES_HIGHLIGHTED : COLOR_GUIDELINES);
					gl.draw(viewState,g2,x,y,width,height);
				}
			
			drawMapDecoration(g2, x, y, width, height);

			LineForm<?> selectedForm = formEditing==null ? null : formEditing.getForm();
			if (forms!=null)
				for (LineForm<?> form:forms)
					if (form!=selectedForm && !highlightedForms.contains(form)) form.drawLines(g2,viewState,false,false);
			
			if (selectedForm!=null) {
				selectedForm.drawLines(g2,viewState,true,false);
				selectedForm.drawPoints(g2,viewState);
			}
			
			for (LineForm<?> hlf:highlightedForms) {
				hlf.drawLines(g2,viewState,false,true);
				hlf.drawPoints(g2,viewState);
			}
		}
		
	}
	
	static void drawPoint(Graphics2D g2, int x, int y, boolean highlighted) {
		int radius = 3;
//		G2.SETCOLOR(COLOR.BLACK);
//		G2.SETXORMODE(COLOR.WHITE);
		g2.setColor(highlighted ? COLOR_POINT_FILL_HIGHLIGHTED : COLOR_POINT_FILL);
		g2.fillOval(x-radius+1, y-radius+1, 2*radius-1, 2*radius-1);
		g2.setColor(COLOR_POINT_CONTOUR);
		g2.drawOval(x-radius, y-radius, 2*radius, 2*radius);
//		g2.setPaintMode();
	}

	@Override
	protected ViewState createViewState() {
		return new ViewState(this);
	}
	class ViewState extends ZoomableCanvas.ViewState {
		
		private ViewState(ZoomableCanvas<?> canvas) {
			super(canvas,0.1f);
			setPlainMapSurface();
			setVertAxisDownPositive(true);
			//debug_showChanges_scalePixelPerLength = true;
		}

		@Override
		protected void determineMinMax(MapLatLong min, MapLatLong max) {
			min.latitude_y  =  -50.0;
			min.longitude_x = -100.0;
			max.latitude_y  =  150.0;
			max.longitude_x =  300.0;
		}
	}
	
	private record GuideResult(Double x, Double y, double dist)
	{
		@Override
		public String toString() {
			String xStr = x==null?"":String.format(Locale.ENGLISH, "X:%1.3f, ", x);
			String yStr = y==null?"":String.format(Locale.ENGLISH, "Y:%1.3f, ", y);
			return String.format("(%s%sdist:%1.3f)", xStr, yStr, dist);
		}
	}
	
	private static class TempGuideResult
	{
		double x,y;
		Double dist;
		TempGuideResult() {
			this.x = 0;
			this.y = 0;
			this.dist = null;
		}
		GuideResult toGuideResult() {
			if (dist==null) return null;
			return new GuideResult(x,y,dist);
		}
	}
	
	static class GuideLine {
		
		enum Type {
			Horizontal("Y"), Vertical("X");
			final String axis;
			Type(String axis) { this.axis = axis; }
		}

		final Type type;
		double pos;
		
		GuideLine(Type type, double pos) {
			this.type = type;
			this.pos = pos;
			Debug.Assert(this.type!=null);
		}

		@Override
		public String toString() {
			return String.format(Locale.ENGLISH, "%s GuideLine @ %s:%1.2f", type, type.axis, pos);
		}

		private static GuideResult stickToGuideLines(double val, Type type, double maxDist, Vector<GuideLine> lines) {
			Double dist = null;
			Double pos = null;
			if (lines!=null)
				for (GuideLine gl:lines)
					if (gl.type==type) {
						double d = Math.abs(gl.pos-val);
						if (d<=maxDist && (dist==null || dist>d)) {
							dist = d;
							pos = gl.pos;
						}
					}
			if (pos!=null && dist!=null)
				switch (type) {
				case Horizontal: return new GuideResult(null, pos, dist);
				case Vertical  : return new GuideResult(pos, null, dist);
				}
			return null;
		}

		private void draw(ViewState viewState, Graphics2D g2, int x, int y, int width, int height) {
			switch (type) {
			case Horizontal:
				int yl = viewState.convertPos_AngleToScreen_LatY(pos);
				g2.drawLine(x, yl, x+width, yl);
				break;
			case Vertical:
				int xl = viewState.convertPos_AngleToScreen_LongX(pos);
				g2.drawLine(xl, y, xl, y+height);
				break;
			}
		}
		
	}
}
