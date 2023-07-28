package net.schwarzbaer.java.tools.lineeditor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.Locale;
import java.util.Vector;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;

import net.schwarzbaer.java.lib.gui.Tables;
import net.schwarzbaer.java.lib.gui.Tables.SimplifiedColumnConfig;
import net.schwarzbaer.java.lib.image.linegeometry.Form;
import net.schwarzbaer.java.lib.image.linegeometry.Math2;
import net.schwarzbaer.java.tools.lineeditor.EditorView.ViewState;
import net.schwarzbaer.java.tools.lineeditor.LineForm.Arc;
import net.schwarzbaer.java.tools.lineeditor.LineForm.Line;
import net.schwarzbaer.java.tools.lineeditor.LineForm.PolyLine;
import net.schwarzbaer.java.tools.lineeditor.LineForm.Arc.ArcPoint;
import net.schwarzbaer.java.tools.lineeditor.LineForm.Arc.ArcPoint.Type;
import net.schwarzbaer.java.tools.lineeditor.LineForm.Line.LinePoint;

abstract class LineFormEditing<HighlightedPointType> {

	static String toString(double[] values, DoubleFunction<? extends String> mapper) {
		return Arrays.toString(toStringArr(values, mapper));
	}
	static String[] toStringArr(double[] values, DoubleFunction<? extends String> mapper) {
		return Arrays.stream(values).mapToObj(mapper).toArray(n->new String[n]);
	}
	
	interface EditableForm<HPType> {
		void setHighlightedPoint(HPType point);
	}
	
	private final LineForm<HighlightedPointType> form;
	protected final ViewState viewState;
	protected final EditorView editorView;
	private Point pickOffset = null;
	private HighlightedPointType selectedPoint = null;
	
	LineFormEditing(LineForm<HighlightedPointType> form, ViewState viewState, EditorView editorView) {
		this.form = form;
		this.viewState = viewState;
		this.editorView = editorView;
	}
	
	void stopEditing() {}
	LineForm<HighlightedPointType> getForm() { return form; }

	protected abstract HighlightedPointType getNext(int x, int y);
	protected abstract void  prepareDragging    (HighlightedPointType selectedPoint);
	protected abstract float getSelectedPointX (HighlightedPointType selectedPoint);
	protected abstract float getSelectedPointY (HighlightedPointType selectedPoint);
	protected abstract void  modifySelectedPoint(HighlightedPointType selectedPoint, int x, int y, Point pickOffset);

	abstract JPanel createValuePanel();

	void keyTyped   (KeyEvent e) {}
	void keyReleased(KeyEvent e) {}
	void keyPressed (KeyEvent e) {}
	
	boolean onClicked(MouseEvent e) { return false; }
	void onEntered (MouseEvent e) { form.setHighlightedPoint(getNext(e.getX(),e.getY())); editorView.repaint(); }
	void onMoved   (MouseEvent e) { form.setHighlightedPoint(getNext(e.getX(),e.getY())); editorView.repaint(); }
	void onExited  (MouseEvent e) { form.setHighlightedPoint(null                      ); editorView.repaint(); }
	
	boolean onPressed (MouseEvent e) {
		if (e.getButton()!=MouseEvent.BUTTON1) return false;
		int x = e.getX();
		int y = e.getY();
		selectedPoint = getNext(x,y);
		form.setHighlightedPoint(selectedPoint);
		if (selectedPoint!=null) {
			prepareDragging(selectedPoint);
			int xs = viewState.convertPos_AngleToScreen_LongX((float) getSelectedPointX(selectedPoint));
			int ys = viewState.convertPos_AngleToScreen_LatY ((float) getSelectedPointY(selectedPoint));
			pickOffset = new Point(xs-x, ys-y);
			Debug.Assert(pickOffset!=null);
			editorView.repaint();
			return true;
		}
		pickOffset = null;
		editorView.repaint();
		return false;
	}
	
	boolean onDragged (MouseEvent e) {
		int x = e.getX();
		int y = e.getY();
		if (selectedPoint!=null) {
			form.setHighlightedPoint(selectedPoint);
			modifySelectedPoint(selectedPoint,x,y,pickOffset);
			editorView.repaint();
			return true;
		}
		editorView.repaint();
		return false;
	}

	boolean onReleased(MouseEvent e) {
		selectedPoint = null;
		form.setHighlightedPoint(null);
		pickOffset = null;
		editorView.repaint();
		return false;
	}

	static LineFormEditing<?> create(LineForm<?> form, ViewState viewState, EditorView editorView, MouseEvent e) {
		if (form instanceof PolyLine) return new PolyLineEditing((PolyLine) form, viewState, editorView, e);
		if (form instanceof Line    ) return new     LineEditing((Line    ) form, viewState, editorView, e);
		if (form instanceof Arc     ) return new      ArcEditing((Arc     ) form, viewState, editorView, e);
		return null;
	}

	protected void setFixed(JTextField field, boolean isFixed) {
		field.setEditable(!isFixed);
		//field.setEnabled (!isFixed);
	}
	
	protected JCheckBox createCheckBox(String title, boolean isSelected, Consumer<Boolean> setValue) {
		JCheckBox comp = new JCheckBox(title,isSelected);
		if (setValue!=null)
			comp.addActionListener(e->{
				setValue.accept(comp.isSelected());
				editorView.repaint();
			});
		return comp;
	}
	
	protected GenericTextField<Double> createDoubleInput(double value, Consumer<Double> setValue) {
		return createDoubleInput(value, setValue, v->true);
	}

	protected GenericTextField<Double> createDoubleInput(double value, Consumer<Double> setValue, Predicate<Double> isOK) {
		Function<String,Double> parse = str->{ try { return Double.parseDouble(str); } catch (NumberFormatException e) { return Double.NaN; } };
		Predicate<Double> isOK2 = v->v!=null && !Double.isNaN(v) && isOK.test(v);
		Function<Double, String> toString = v->v==null ? "" : v.toString();
		return new GenericTextField<>(value, toString, parse, isOK2, setValue);
	}

	protected GenericTextField<String> createTextInput(String value, Consumer<String> setValue, Predicate<String> isOK) {
		return new GenericTextField<>(value, v->v, v->v, isOK, setValue, setValue);
	}
	
	protected class GenericTextField<V> extends JTextField {
		private static final long serialVersionUID = 1942488481760358728L;
		
		private final Function<V, String> toString;
		private final Color defaultBG;
		private final Function<String, V> parse;
		private final Predicate<V> isOK;

		GenericTextField(V value, Function<V,String> toString, Function<String,V> parse, Predicate<V> isOK, Consumer<V> setValue) {
			this(value, toString, parse, isOK, setValue, null);
		}
		GenericTextField(V value, Function<V,String> toString, Function<String,V> parse, Predicate<V> isOK, Consumer<V> setValue, Consumer<V> setValueWhileAdjusting) {
			super(toString.apply(value),5);
			this.toString = toString;
			this.parse = parse;
			this.isOK = isOK;
			defaultBG = getBackground();
			if (setValueWhileAdjusting!=null) {
				addCaretListener (e -> {
					readTextField(v -> {
						setValueWhileAdjusting.accept(v);
						editorView.repaint();
					});
				});
			}
			Consumer<V> modifiedSetValue = d -> {
				setValue.accept(d);
				editorView.repaint();
			};
			addActionListener(e->{ readTextField(modifiedSetValue); });
			addFocusListener(new FocusListener() {
				@Override public void focusLost  (FocusEvent e) { readTextField(modifiedSetValue); }
				@Override public void focusGained(FocusEvent e) {}
			});
		}
		
		void setValue(V value) { setText(toString.apply(value)); }
		
		private void readTextField(Consumer<V> setValue) {
			V d = parse.apply(getText());
			if (isOK.test(d)) {
				setBackground(defaultBG);
				setValue.accept(d);
			} else {
				setBackground(Color.RED);
			}
		}
	}

	static class LineEditing extends LineFormEditing<LinePoint> {
		
		private GenericTextField<Double> x1Field = null;
		private GenericTextField<Double> y1Field = null;
		private GenericTextField<Double> x2Field = null;
		private GenericTextField<Double> y2Field = null;
		private boolean isX1Fixed = false;
		private boolean isY1Fixed = false;
		private boolean isX2Fixed = false;
		private boolean isY2Fixed = false;
		private final Line line;
		
		private LineEditing(Line line, ViewState viewState, EditorView editorView, MouseEvent e) {
			super(line, viewState, editorView);
			this.line = line;
			this.line.setHighlightedPoint(e==null ? null : getNext(e.getX(),e.getY()));
		}
				
		@Override JPanel createValuePanel() {
			int i=0;
			JPanel panel = new JPanel(new GridBagLayout());
			panel.setBorder(BorderFactory.createTitledBorder("Line Values"));
			GridBagConstraints c = new GridBagConstraints();
			c.fill = GridBagConstraints.BOTH;
			c.weighty=0;
			c.weightx=0; c.gridx=0; i=0;
			c.gridy=i++; panel.add(new JLabel("X1: "),c);
			c.gridy=i++; panel.add(new JLabel("Y1: "),c);
			c.gridy=i++; panel.add(new JLabel("X2: "),c);
			c.gridy=i++; panel.add(new JLabel("Y2: "),c);
			c.weightx=1; c.gridx=1; i=0;
			c.gridy=i++; panel.add(x1Field=createDoubleInput(line.x1, v->line.x1=v),c);
			c.gridy=i++; panel.add(y1Field=createDoubleInput(line.y1, v->line.y1=v),c);
			c.gridy=i++; panel.add(x2Field=createDoubleInput(line.x2, v->line.x2=v),c);
			c.gridy=i++; panel.add(y2Field=createDoubleInput(line.y2, v->line.y2=v),c);
			c.weightx=0; c.gridx=2; i=0;
			c.gridy=i++; panel.add(createCheckBox("fixed", isX1Fixed, b->setFixed(x1Field,isX1Fixed=b)),c);
			c.gridy=i++; panel.add(createCheckBox("fixed", isY1Fixed, b->setFixed(y1Field,isY1Fixed=b)),c);
			c.gridy=i++; panel.add(createCheckBox("fixed", isX2Fixed, b->setFixed(x2Field,isX2Fixed=b)),c);
			c.gridy=i++; panel.add(createCheckBox("fixed", isY2Fixed, b->setFixed(y2Field,isY2Fixed=b)),c);
			c.weighty=1; c.weightx=1;
			c.gridx=0; c.gridy=i; c.gridwidth=3;
			panel.add(new JLabel(),c);
			return panel;
		}

		@Override protected LinePoint getNext(int x, int y) {
			double xu = viewState.convertPos_ScreenToAngle_LongX(x);
			double yu = viewState.convertPos_ScreenToAngle_LatY (y);
			double maxDist = viewState.convertLength_ScreenToLength(EditorView.MAX_NEAR_DISTANCE);
			
			double d1 = Math2.dist(line.x1, line.y1, xu, yu);
			double d2 = Math2.dist(line.x2, line.y2, xu, yu);
			if (d1<d2 && d1<maxDist) return LinePoint.P1;
			if (d2<d1 && d2<maxDist) return LinePoint.P2;
			return null;
		}
		
		@Override protected void prepareDragging(LinePoint selectedPoint) {}
		@Override protected float getSelectedPointX(LinePoint selectedPoint) {
			switch (selectedPoint) {
			case P1: return (float) line.x1;
			case P2: return (float) line.x2;
			}
			Debug.Assert(false);
			return 0;
		}

		@Override protected float getSelectedPointY(LinePoint selectedPoint) {
			switch (selectedPoint) {
			case P1: return (float) line.y1;
			case P2: return (float) line.y2;
			}
			Debug.Assert(false);
			return 0;
		}

		@Override
		protected void modifySelectedPoint(LinePoint selectedPoint, int x, int y, Point pickOffset) {
			x+=pickOffset.x;
			y+=pickOffset.y;
			Point2D.Double p;
			switch (selectedPoint) {
			case P1:
				//if (!isX1Fixed) x1Field.setValue(line.x1 = editorView.stickToGuideLineX(viewState.convertPos_ScreenToAngle_LongX(x)));
				//if (!isY1Fixed) y1Field.setValue(line.y1 = editorView.stickToGuideLineY(viewState.convertPos_ScreenToAngle_LatY (y)));
				p = editorView.stickToGuides_px( x,y, isX1Fixed, isY1Fixed );
				if (!isX1Fixed) x1Field.setValue(line.x1 = p.x);
				if (!isY1Fixed) y1Field.setValue(line.y1 = p.y);
				break;
			case P2:
				//if (!isX2Fixed) x2Field.setValue(line.x2 = editorView.stickToGuideLineX(viewState.convertPos_ScreenToAngle_LongX(x)));
				//if (!isY2Fixed) y2Field.setValue(line.y2 = editorView.stickToGuideLineY(viewState.convertPos_ScreenToAngle_LatY (y)));
				p = editorView.stickToGuides_px( x,y, isX2Fixed, isY2Fixed );
				if (!isX2Fixed) x2Field.setValue(line.x2 = p.x);
				if (!isY2Fixed) y2Field.setValue(line.y2 = p.y);
				break;
			}
		}
	}
	
	static class ArcEditing extends LineFormEditing<ArcPoint> {
		
		private GenericTextField<Double>     cxField = null;
		private GenericTextField<Double>     cyField = null;
		private GenericTextField<Double>      rField = null;
		private GenericTextField<Double> aStartField = null;
		private GenericTextField<Double>   aEndField = null;
		private boolean     isCxFixed = false;
		private boolean     isCyFixed = false;
		private boolean      isRFixed = false;
		private boolean isAStartFixed = false;
		private boolean   isAEndFixed = false;
		
		private double[] glAngles = null;
		private double maxGlAngle = Double.NaN;
		private final Arc arc;
		
		private ArcEditing(Arc arc, ViewState viewState, EditorView editorView, MouseEvent e) {
			super(arc, viewState, editorView);
			this.arc = arc;
			this.arc.setHighlightedPoint(e==null ? null : getNext(e.getX(),e.getY()));
		}
		
		@Override JPanel createValuePanel() {
			JPanel panel = new JPanel(new GridBagLayout());
			panel.setBorder(BorderFactory.createTitledBorder("Arc Values"));
			GridBagConstraints c = new GridBagConstraints();
			int i=0;
			c.fill = GridBagConstraints.BOTH;
			c.weighty=0;
			c.weightx=0; c.gridx=0; i=0;
			c.gridy=i++; panel.add(new JLabel("Center X: "),c);
			c.gridy=i++; panel.add(new JLabel("Center Y: "),c);
			c.gridy=i++; panel.add(new JLabel("Radius: "),c);
			c.gridy=i++; panel.add(new JLabel("Start Angle (deg): "),c);
			c.gridy=i++; panel.add(new JLabel("End Angle (deg): "),c);
			c.weightx=1; c.gridx=1; i=0;
			c.gridy=i++; panel.add(    cxField=createDoubleInput(arc.xC    , v->arc.xC    =v),c);
			c.gridy=i++; panel.add(    cyField=createDoubleInput(arc.yC    , v->arc.yC    =v),c);
			c.gridy=i++; panel.add(     rField=createDoubleInput(arc.r     , v->arc.r     =v, v->v>0),c);
			c.gridy=i++; panel.add(aStartField=createDoubleInput(arc.aStart*180/Math.PI, v->arc.aStart=v/180*Math.PI, v->v<=arc.aEnd  *180/Math.PI),c);
			c.gridy=i++; panel.add(  aEndField=createDoubleInput(arc.aEnd  *180/Math.PI, v->arc.aEnd  =v/180*Math.PI, v->v>=arc.aStart*180/Math.PI),c);
			c.weightx=0; c.gridx=2; i=0;
			c.gridy=i++; panel.add(createCheckBox("fixed",     isCxFixed, b->setFixed(    cxField,    isCxFixed=b)),c);
			c.gridy=i++; panel.add(createCheckBox("fixed",     isCyFixed, b->setFixed(    cyField,    isCyFixed=b)),c);
			c.gridy=i++; panel.add(createCheckBox("fixed",      isRFixed, b->setFixed(     rField,     isRFixed=b)),c);
			c.gridy=i++; panel.add(createCheckBox("fixed", isAStartFixed, b->setFixed(aStartField,isAStartFixed=b)),c);
			c.gridy=i++; panel.add(createCheckBox("fixed",   isAEndFixed, b->setFixed(  aEndField,  isAEndFixed=b)),c);
			c.weighty=1; c.weightx=1;
			c.gridx=0; c.gridy=i; c.gridwidth=3;
			panel.add(new JLabel(),c);
			return panel;
		}

		@Override protected ArcPoint getNext(int x, int y) {
			double maxDist = viewState.convertLength_ScreenToLength(EditorView.MAX_NEAR_DISTANCE);
			double xM = viewState.convertPos_ScreenToAngle_LongX(x);
			double yM = viewState.convertPos_ScreenToAngle_LatY (y);
			
			double xC = arc.xC;
			double yC = arc.yC;
			double xS = xC+arc.r*Math.cos(arc.aStart);
			double yS = yC+arc.r*Math.sin(arc.aStart);
			double xE = xC+arc.r*Math.cos(arc.aEnd  );
			double yE = yC+arc.r*Math.sin(arc.aEnd  );
			
			double dC = Math2.dist(xC, yC, xM, yM);
			double dS = Math2.dist(xS, yS, xM, yM);
			double dE = Math2.dist(xE, yE, xM, yM);
			if (dC<dS && dC<dE && dC<maxDist && (!isCxFixed || !isCyFixed)) return new ArcPoint(ArcPoint.Type.Center, xC,yC);
			if (dS<dE && dS<dC && dS<maxDist &&  !isAStartFixed) return new ArcPoint(ArcPoint.Type.Start , xS,yS);
			if (dE<dC && dE<dS && dE<maxDist &&  !isAEndFixed  ) return new ArcPoint(ArcPoint.Type.End   , xE,yE);
			
			if (Math.abs(dC-arc.r) < maxDist && !isRFixed) {
				double angle = Math2.angle(xC, yC, xM, yM);
				if (Math2.isInsideAngleRange(arc.aStart, arc.aEnd, angle)) {
					double xR = xC+arc.r*Math.cos(angle);
					double yR = yC+arc.r*Math.sin(angle);
					return new ArcPoint(ArcPoint.Type.Radius,xR,yR);
				}
			}
			return null;
		}

		@Override protected float getSelectedPointX(ArcPoint selectedPoint) { return (float) selectedPoint.x; }
		@Override protected float getSelectedPointY(ArcPoint selectedPoint) { return (float) selectedPoint.y; }
		@Override protected void  prepareDragging  (ArcPoint selectedPoint) {
			if (selectedPoint.type==Type.Start || selectedPoint.type==Type.End) {
				glAngles = computeIntersectionPointsWithGuideLines(arc.xC,arc.yC,arc.r);
				maxGlAngle = viewState.convertLength_ScreenToLength(EditorView.MAX_GUIDELINE_DISTANCE)/arc.r;
				//System.out.printf(Locale.ENGLISH, "maxGlAngle: %1.4f (%1.2f°)%n", maxGlAngle, maxGlAngle*180/Math.PI);
				//System.out.printf(Locale.ENGLISH, "glAngles:%n");
				//System.out.printf(Locale.ENGLISH, "   %s%n", toString(glAngles, d->String.format(Locale.ENGLISH, "%1.4f", d            )));
				//System.out.printf(Locale.ENGLISH, "   %s%n", toString(glAngles, d->String.format(Locale.ENGLISH, "%1.2f°", d*180/Math.PI)));
			}
		}

		private double[] computeIntersectionPointsWithGuideLines(double xC, double yC, double r) {
			Vector<Double> anglesVec = new Vector<>();
			editorView.forEachGuideLines((type,pos)->{
				switch (type) {
				case Horizontal:
					if (yC-r<pos && pos<yC+r) {
						double a = Math.asin((pos-yC)/r);
						anglesVec.add(a);
						anglesVec.add(Math.PI-a);
					}
					break;
				case Vertical:
					if (xC-r<pos && pos<xC+r) {
						double a = Math.acos((pos-xC)/r);
						anglesVec.add( a);
						anglesVec.add(-a);
					}
					break;
				}
			});
			double[] anglesArr = anglesVec.stream().mapToDouble(d->d).toArray();
			for (int i=0; i<anglesArr.length; i++) {
				while (anglesArr[i] <  0        ) anglesArr[i] += Math.PI*2;
				while (anglesArr[i] >= Math.PI*2) anglesArr[i] -= Math.PI*2;
			}
			Arrays.sort(anglesArr);
			return anglesArr;
		}
		
		@Override protected void modifySelectedPoint(ArcPoint selectedPoint, int x, int y, Point pickOffset) {
			x+=pickOffset.x;
			y+=pickOffset.y;
			switch (selectedPoint.type) {
			case Radius:
				//selectedPoint.x = editorView.stickToGuideLineX(viewState.convertPos_ScreenToAngle_LongX(x));
				//selectedPoint.y = editorView.stickToGuideLineY(viewState.convertPos_ScreenToAngle_LatY (y));
				selectedPoint.set(editorView.stickToGuides_px(x,y, false, false));
				arc.r = Math2.dist(arc.xC, arc.yC, selectedPoint.x, selectedPoint.y);
				rField.setValue(arc.r);
				break;
			case Center:
				//if (!isCxFixed) cxField.setValue(selectedPoint.x = arc.xC = editorView.stickToGuideLineX(viewState.convertPos_ScreenToAngle_LongX(x)));
				//if (!isCyFixed) cyField.setValue(selectedPoint.y = arc.yC = editorView.stickToGuideLineY(viewState.convertPos_ScreenToAngle_LatY (y)));
				Point2D.Double p = editorView.stickToGuides_px(x,y, isCxFixed, isCyFixed);
				if (!isCxFixed) cxField.setValue(selectedPoint.x = arc.xC = p.x);
				if (!isCyFixed) cyField.setValue(selectedPoint.y = arc.yC = p.y);
				break;
			case End  : {
				double a = computeAngle(x,y); 
				while (a<arc.aStart          ) a+=Math.PI*2;
				while (a>arc.aStart+Math.PI*2) a-=Math.PI*2;
				//if (form.aEnd != a) System.out.printf(Locale.ENGLISH, "form.aEnd: %1.4f%n", a);
				arc.aEnd = a;
				aEndField.setValue(arc.aEnd*180/Math.PI);
			} break;
			case Start: {
				double a = computeAngle(x,y); 
				while (a>arc.aEnd          ) a-=Math.PI*2;
				while (a<arc.aEnd-Math.PI*2) a+=Math.PI*2;
				//if (form.aStart != a) System.out.printf(Locale.ENGLISH, "form.aStart: %1.4f%n", a);
				arc.aStart = a;
				aStartField.setValue(arc.aStart*180/Math.PI);
			} break;
			}
		}

		private double computeAngle(int x, int y) {
			double xM = viewState.convertPos_ScreenToAngle_LongX(x);
			double yM = viewState.convertPos_ScreenToAngle_LatY (y);
			double aM = Math2.angle(arc.xC, arc.yC, xM, yM);
			double aMinDist = Math.PI*2;
			Double aMin = null;
			double[] aDistArr = new double[glAngles.length];
			for (int i=0; i<glAngles.length; i++) {
				double a = glAngles[i];
				double aDist = Math.abs(Math2.computeAngleDist(a,aM));
				aDistArr[i] = aDist;
				if (aDist<maxGlAngle && (aMin==null || aDist<aMinDist)) {
					aMinDist = aDist;
					aMin = a;
				}
			}
			//System.out.printf(Locale.ENGLISH, "AngleDistances: %s%n", toString(aDistArr, d->String.format(Locale.ENGLISH, "%1.2f°", d*180/Math.PI)));
			if (aMin!=null) return aMin;
			return aM;
		}
	}

	static class PolyLineEditing extends LineFormEditing<Integer> implements PolyLine.HighlightListener {
		
		private final PolyLine polyLine;
		private boolean isXFixed;
		private boolean isYFixed;
		private JTable pointList = null;
		private JButton btnRemove = null;
		private PointListModel pointListModel = null;
		public SimplifiedColumnConfig config;
		
		private PolyLineEditing(PolyLine polyLine, ViewState viewState, EditorView editorView, MouseEvent e) {
			super(polyLine, viewState, editorView);
			Debug.Assert(polyLine!=null);
			this.polyLine = polyLine;
			this.polyLine.setHighlightedPoint(e==null ? null : getNext(e.getX(),e.getY()));
			this.polyLine.setHighlightListener(this);
		}
		@Override void stopEditing() { polyLine.setHighlightListener(this); }

		@Override public void highlightedPointChanged(Integer index_) {
			int newIndex = index_==null ? -1 : index_.intValue();
			int oldIndex = pointList.getSelectedRow();
			if (newIndex!=oldIndex) {
				cancelCellEditing();
				if (newIndex<0) pointList.clearSelection();
				else pointList.setRowSelectionInterval(newIndex, newIndex);
			}
		}
		private void cancelCellEditing() {
			TableCellEditor editor = pointList.getCellEditor();
			if (editor!=null) editor.cancelCellEditing();
		}

		@Override JPanel createValuePanel() {
			pointList = new JTable(pointListModel = new PointListModel());
			pointList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			pointList.getSelectionModel().addListSelectionListener(e->{
				int index = pointList.getSelectedRow();
				polyLine.setHighlightedPoint(index<0 ? null : index);
				editorView.repaint();
				setButtonsEnabled(index>=0);
			});
			//pointListModel.setColumnWidths(pointList);
			//pointList.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
			pointList.setDefaultRenderer(Double.class, new PointListRenderer());
			JScrollPane pointListScrollPane = new JScrollPane(pointList);
			pointListScrollPane.setPreferredSize(new Dimension(200, 200));
			
			JPanel buttonPanel = new JPanel(new GridBagLayout());
			buttonPanel.add(createCheckBox("X Fixed", isXFixed, b->{ isXFixed=b; cancelCellEditing(); pointList.repaint(); }));
			buttonPanel.add(createCheckBox("Y Fixed", isYFixed, b->{ isYFixed=b; cancelCellEditing(); pointList.repaint(); }));
			buttonPanel.add(btnRemove = LineEditor.createButton("Remove", false, e->removePoint(pointList.getSelectedRow())));
			
			JPanel panel = new JPanel(new BorderLayout(3,3));
			panel.setBorder(BorderFactory.createTitledBorder("PolyLine Values"));
			panel.add(pointListScrollPane,BorderLayout.CENTER);
			panel.add(buttonPanel,BorderLayout.SOUTH);
			
			return panel;
		}
		
		private void setButtonsEnabled(boolean enabled) {
			btnRemove.setEnabled(enabled);
		}
		
		private void removePoint(int index) {
			if (index<0 || index>=polyLine.points.size()) return;
			polyLine.points.remove(index);
			polyLine.setHighlightedPoint(null);
			cancelCellEditing();
			pointListModel.fireTableRowRemoved(index);
			editorView.repaint();
		}
		
		enum ColumnID implements Tables.SimplifiedColumnIDInterface {
			X("X"), Y("Y");
			private final SimplifiedColumnConfig config;
			ColumnID(String name) { config = new SimplifiedColumnConfig(name, Double.class, 20, -1, 50, 50); }
			@Override public SimplifiedColumnConfig getColumnConfig() { return config; }
		}

		private class PointListModel extends Tables.SimplifiedTableModel<ColumnID> {
			
			protected PointListModel() { super(ColumnID.values()); }
			
			@Override public void fireTableRowAdded  (int rowIndex) { super.fireTableRowAdded  (rowIndex); }
			@Override public void fireTableRowRemoved(int rowIndex) { super.fireTableRowRemoved(rowIndex); }
			@Override public void fireTableRowUpdate (int rowIndex) { super.fireTableRowUpdate (rowIndex); }
			
			@Override public int getRowCount() { return polyLine.points.size(); }
			@Override public Object getValueAt(int rowIndex, int columnIndex, ColumnID columnID) {
				if (rowIndex<0 || rowIndex>=polyLine.points.size()) return null;
				Form.PolyLine.Point p = polyLine.points.get(rowIndex);
				switch (columnID) {
				case X: return p.x;
				case Y: return p.y;
				}
				return null;
			}

			@Override
			protected boolean isCellEditable(int rowIndex, int columnIndex, ColumnID columnID) {
				switch (columnID) {
				case X: return !isXFixed;
				case Y: return !isYFixed;
				}
				return false;
			}

			@Override
			protected void setValueAt(Object aValue, int rowIndex, int columnIndex, ColumnID columnID) {
				if (rowIndex<0 || rowIndex>=polyLine.points.size()) return;
				Form.PolyLine.Point p = polyLine.points.get(rowIndex);
				boolean resetRow = false;
				switch (columnID) {
				case X: { double d=(double)aValue; if (Double.isNaN(d)) resetRow=true; else p.x=d; } break;
				case Y: { double d=(double)aValue; if (Double.isNaN(d)) resetRow=true; else p.y=d; } break;
				}
				if (resetRow)
					SwingUtilities.invokeLater(()->pointListModel.fireTableRowUpdate(rowIndex));
				editorView.repaint();
			}
		}
		
		private static final Color COLOR_BACKGROUND_FIXED = new Color(0xf0f0f0);
		private static final Color COLOR_FOREGROUND_FIXED = Color.GRAY;
		
		private class PointListRenderer extends DefaultTableCellRenderer {
			private static final long serialVersionUID = 2427799200069333655L;

			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
				Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
				
				if (value instanceof Double)
					setText(String.format(Locale.ENGLISH, "%1.3f", (Double) value));
				
				ColumnID columnID = pointListModel.getColumnID(column);
				boolean isFixed = (columnID==ColumnID.X && isXFixed) || (columnID==ColumnID.Y && isYFixed);
				if (!isSelected)
					setBackground(isFixed ? COLOR_BACKGROUND_FIXED : table.getBackground());
				setForeground(isFixed ? COLOR_FOREGROUND_FIXED : isSelected ? table.getSelectionForeground() : table.getForeground());
				
				setHorizontalAlignment(RIGHT);
				return component;
			}
		}
		
		
		@Override protected Integer getNext(int x, int y) {
			double xu = viewState.convertPos_ScreenToAngle_LongX(x);
			double yu = viewState.convertPos_ScreenToAngle_LatY (y);
			double maxDist = viewState.convertLength_ScreenToLength(EditorView.MAX_NEAR_DISTANCE);
			
			Integer index = null;
			double minDist = 0;
			for (int i=0; i<polyLine.points.size(); i++) {
				Form.PolyLine.Point p = polyLine.points.get(i);
				double d = Math2.dist(p.x, p.y, xu, yu);
				if (d<maxDist && (index==null || d<minDist)) {
					minDist = d;
					index = i;
				}
			}
			return index;
		}

		@Override protected void  prepareDragging  (Integer selectedPoint) {}
		@Override protected float getSelectedPointX(Integer selectedPoint) { return (float) polyLine.points.get(selectedPoint).x; }
		@Override protected float getSelectedPointY(Integer selectedPoint) { return (float) polyLine.points.get(selectedPoint).y; }

		@Override protected void modifySelectedPoint(Integer selectedPoint, int x, int y, Point pickOffset) {
			Form.PolyLine.Point p = polyLine.points.get(selectedPoint);
			p.set( editorView.stickToGuides_px( x+pickOffset.x, y+pickOffset.y, isXFixed, isYFixed ) );
			pointListModel.fireTableRowUpdate(selectedPoint);
		}
		
		@Override void onEntered(MouseEvent e) {
			if (e.isControlDown()) { setNextNewPoint(e); return; }
			if (polyLine.hasNextNewPoint()) clearNextNewPoint();
			super.onEntered(e);
		}
		@Override void onMoved(MouseEvent e) {
			if (e.isControlDown()) { setNextNewPoint(e); return; }
			if (polyLine.hasNextNewPoint()) clearNextNewPoint();
			super.onMoved(e);
		}
		@Override boolean onDragged(MouseEvent e) {
			if (e.isControlDown()) { setNextNewPoint(e); return true; }
			if (polyLine.hasNextNewPoint()) clearNextNewPoint();
			return super.onDragged(e);
		}
		@Override void onExited(MouseEvent e) {
			clearNextNewPoint();
			super.onExited(e);
		}
		@Override boolean onClicked(MouseEvent e) {
			if (e.isControlDown() && e.getButton()==MouseEvent.BUTTON1) { addNextNewPoint(e); return true; }
			checkNextNewPoint(e);
			return super.onClicked(e);
		}
		@Override boolean onPressed(MouseEvent e) {
			if (e.isControlDown() && e.getButton()==MouseEvent.BUTTON1) return true;
			checkNextNewPoint(e);
			return super.onPressed(e);
		}
		@Override boolean onReleased(MouseEvent e) {
			if (e.isControlDown() && e.getButton()==MouseEvent.BUTTON1) return true;
			checkNextNewPoint(e);
			return super.onReleased(e);
		}
		@Override void keyReleased(KeyEvent e) {
			checkNextNewPoint(e);
			super.keyReleased(e);
		}
		
		private void updateNextNewPoint(MouseEvent e) {
			double xM = viewState.convertPos_ScreenToAngle_LongX(e.getX());
			double yM = viewState.convertPos_ScreenToAngle_LatY (e.getY());
			double maxDist = viewState.convertLength_ScreenToLength(EditorView.MAX_GUIDELINE_DISTANCE);
			if (polyLine.setNextNewPointOnLine(xM,yM,maxDist)) return;
			//double x = editorView.stickToGuideLineX(xM);
			//double y = editorView.stickToGuideLineY(yM);
			Point2D.Double p = editorView.stickToGuides( xM,yM, false, false );
			polyLine.setNextNewPoint(p.x,p.y);
		}
		private void setNextNewPoint(MouseEvent e) {
			polyLine.setHighlightedPoint(null);
			updateNextNewPoint(e);
			editorView.repaint();
		}
		private void addNextNewPoint(MouseEvent e) {
			updateNextNewPoint(e);
			int index = polyLine.addNextNewPoint();
			cancelCellEditing();
			pointListModel.fireTableRowAdded(index);
			editorView.repaint();
		}
		private void checkNextNewPoint(InputEvent e) {
			if (!e.isControlDown() && polyLine.hasNextNewPoint()) clearNextNewPoint();
		}
		private void clearNextNewPoint() {
			polyLine.clearNextNewPoint();
			editorView.repaint();
		}
		
		
		
	}

}