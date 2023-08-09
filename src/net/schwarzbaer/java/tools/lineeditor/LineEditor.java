package net.schwarzbaer.java.tools.lineeditor;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionListener;
import java.awt.geom.Rectangle2D;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JToggleButton;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListDataListener;

import net.schwarzbaer.java.lib.gui.Disabler;
import net.schwarzbaer.java.lib.gui.ZoomableCanvas;
import net.schwarzbaer.java.lib.image.linegeometry.Form;
import net.schwarzbaer.java.tools.lineeditor.EditorView.GuideLine;
import net.schwarzbaer.java.tools.lineeditor.LineForm.FormType;

public class LineEditor
{
	public record GuideLinesChangedEvent(GuideLinesChangedEvent.Type type, String caller)
	{
		public enum Type { Added, Removed, Changed }
	}
	
	public record FormsChangedEvent(FormsChangedEvent.Type type, String caller)
	{
		public enum Type { Added, Removed, Changed }
	}
	
	public interface Context
	{
		void switchOptionsPanel(JComponent panel);
		boolean canCreateNewForm();
		void replaceForms(Form[] forms);
		void guideLinesChanged(GuideLinesChangedEvent event);
		void formsChanged(FormsChangedEvent event);
	}
	
	private LineForm<?>[] lineforms = null;
	
	private final EditorView editorView;
	private final GeneralOptionPanel generalOptionPanel;
	private final EditorViewContextMenu editorViewContextMenu;
	private GuideLinesStorage guideLinesStorage;

	public LineEditor(Rectangle2D.Double initialViewRect, Context context, EditorViewFeature... features) {
		guideLinesStorage = null;
		
		generalOptionPanel = new GeneralOptionPanel(new GeneralOptionPanel.Context() {
			@Override public void repaintView() { editorView.repaint(); }
			@Override public Rectangle2D.Float getViewRectangle() { return editorView.getViewRectangle(); }

			@Override public void changeHighlightedForms    (List<LineForm<?>> forms) { editorView.setHighlightedForms(forms); }
			@Override public void changeSelectedForm        (LineForm<?> form       ) { editorView.setSelectedForm    (form); }
			@Override public void changeHighlightedGuideLine(GuideLine guideLine    ) { editorView.setHighlightedGuideLine(guideLine); }
			
			
			@Override
			public void guideLineChanged() {
				context.guideLinesChanged(new GuideLinesChangedEvent(GuideLinesChangedEvent.Type.Changed, "GeneralOptionPanel.Context.guideLineChanged"));
			}
			
			@Override
			public boolean canCreateNewGuideLine()
			{
				return guideLinesStorage!=null;
			}
			
			@Override
			public void addGuideLine(GuideLine guideLine) {
				if (guideLinesStorage==null) return;
				if (guideLine==null) return;
				guideLinesStorage.guideLines.add(guideLine);
				editorView        .setGuideLines(guideLinesStorage.guideLines);
				generalOptionPanel.setGuideLines(guideLinesStorage.guideLines);
				context.guideLinesChanged(new GuideLinesChangedEvent(GuideLinesChangedEvent.Type.Added, "GeneralOptionPanel.Context.addGuideLine"));
			}

			@Override
			public void removeGuideLine(int index) {
				if (guideLinesStorage==null) return;
				if (index<0 || index>=guideLinesStorage.guideLines.size()) return;
				guideLinesStorage.guideLines.remove(index);
				editorView        .setGuideLines(guideLinesStorage.guideLines);
				generalOptionPanel.setGuideLines(guideLinesStorage.guideLines);
				context.guideLinesChanged(new GuideLinesChangedEvent(GuideLinesChangedEvent.Type.Removed, "GeneralOptionPanel.Context.removeGuideLine"));
			}

			@Override
			public void addForm(LineForm<?> form) {
				if (form==null) return;
				LineForm<?>[] newArr = lineforms==null ? new LineForm[1] : Arrays.copyOf(lineforms, lineforms.length+1);
				newArr[newArr.length-1] = form;
				setNewArray(newArr);
				context.formsChanged(new FormsChangedEvent(FormsChangedEvent.Type.Added, "GeneralOptionPanel.Context.addForm"));
			}
			@Override
			public void addForms(Vector<LineForm<?>> forms) {
				if (forms==null || forms.isEmpty()) return;
				LineForm<?>[] newArr = lineforms==null ? new LineForm[forms.size()] : Arrays.copyOf(lineforms, lineforms.length+forms.size());
				int offset = lineforms==null ? 0 : lineforms.length;
				for (int i=0; i<forms.size(); i++)
					newArr[offset+i] = forms.get(i);
				setNewArray(newArr);
				context.formsChanged(new FormsChangedEvent(FormsChangedEvent.Type.Added, "GeneralOptionPanel.Context.addForms"));
			}
			@Override
			public void removeForms(List<LineForm<?>> forms) {
				if (forms==null || forms.isEmpty()) return;
				Vector<LineForm<?>> vec = new Vector<>(Arrays.asList(lineforms));
				for (LineForm<?> rf:forms) vec.remove(rf);
				LineForm<?>[] newArr = vec.toArray(new LineForm<?>[vec.size()]);
				setNewArray(newArr);
				context.formsChanged(new FormsChangedEvent(FormsChangedEvent.Type.Removed, "GeneralOptionPanel.Context.removeForms"));
			}

			private void setNewArray(LineForm<?>[] newArr) {
				lineforms = newArr;
				editorView.setForms(lineforms);
				generalOptionPanel.setForms(lineforms);
				if (context.canCreateNewForm()) {
					context.replaceForms(LineForm.convert(lineforms));
				}
			}
			@Override
			public boolean canCreateNewForm()
			{
				return context.canCreateNewForm();
			}
			
		});
		generalOptionPanel.setPreferredSize(new Dimension(200, 200));
		
		editorView = new EditorView(initialViewRect, features, new EditorView.Context() {
			boolean lastPanelWasFormPanel = false;
			
			@Override public void updateHighlightedForms(HashSet<LineForm<?>> forms) {
				if (lineforms==null)
					generalOptionPanel.setSelectedForms(new int[0]);
				else {
					Vector<Integer> indices = new Vector<>();
					for (int i=0; i<lineforms.length; i++) {
						LineForm<?> form = lineforms[i];
						if (forms.contains(form)) indices.add(i);
					}
					generalOptionPanel.setSelectedForms(indices.stream().mapToInt(v->v).toArray());
				}
			}
			@Override public void setValuePanel(JPanel panel) {
				if (panel == null)
				{
					context.switchOptionsPanel(generalOptionPanel);
					if (lastPanelWasFormPanel)
						context.formsChanged(new FormsChangedEvent(FormsChangedEvent.Type.Changed, "EditorView.Context.setValuePanel"));
				}
				else
					context.switchOptionsPanel(createReturnWrapperPanel(panel, ()->editorView.deselect()));
				lastPanelWasFormPanel = panel!=null;
			}
			@Override public void showsContextMenu(int x, int y) {
				editorViewContextMenu.prepareToShow();
				editorViewContextMenu.show(editorView, x,y);
			}
		});
		editorView.setPreferredSize(500, 500);
		editorViewContextMenu = new EditorViewContextMenu(editorView, features);
	}
	
	public Component getEditorView()
	{
		return editorView;
	}

	public JComponent getInitialOptionsPanel()
	{
		return generalOptionPanel;
	}

	public void init()
	{
		editorView.reset();
	}

	public void fitViewToContent(Rectangle2D.Double minViewSize)
	{
		editorView.getViewState().setMinViewSize(minViewSize);
		editorView.reset();
	}

	private static JPanel createReturnWrapperPanel(JPanel panel, Runnable returnAction)
	{
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.add(panel, BorderLayout.CENTER);
		wrapper.add(createButton("return", e->returnAction.run()), BorderLayout.SOUTH);
		return wrapper;
	}

	static <A, C extends JComponent> C addToDisabler(Disabler<A> disabler, A disableTag, C component) {
		disabler.add(disableTag, component);
		return component;
	}

	static JToggleButton createToggleButton(String title, ButtonGroup bg, boolean selected, ActionListener al) {
		JToggleButton comp = new JToggleButton(title,selected);
		if (bg!=null) bg.add(comp);
		if (al!=null) comp.addActionListener(al);
		return comp;
	}

	static JButton createButton(String title, ActionListener al) {
		return createButton(title, true, al);
	}
	static JButton createButton(String title, boolean enabled, ActionListener al) {
		JButton comp = new JButton(title);
		comp.setEnabled(enabled);
		if (al!=null) comp.addActionListener(al);
		return comp;
	}
	
	static JMenuItem createMenuItem(String title, ActionListener al) {
		JMenuItem comp = new JMenuItem(title);
		if (al!=null) comp.addActionListener(al);
		return comp;
	}

	static JCheckBoxMenuItem createCheckBoxMI(String title, boolean isSelected, Consumer<Boolean> setValue) {
		JCheckBoxMenuItem comp = new JCheckBoxMenuItem(title, isSelected);
		if (setValue!=null) comp.addActionListener(e->setValue.accept(comp.isSelected()));
		return comp;
	}

	public void setForms(Form[] forms)
	{
		lineforms = LineForm.convert(forms);
		editorView.setForms(lineforms);
		generalOptionPanel.setForms(lineforms);
	}
	
	public void setGuideLines(GuideLinesStorage guideLinesStorage)
	{
		this.guideLinesStorage = guideLinesStorage;
		Vector<GuideLine> guideLines = guideLinesStorage==null ? null : guideLinesStorage.guideLines;
		editorView        .setGuideLines(guideLines);
		generalOptionPanel.setGuideLines(guideLines);
	}
	public static void drawForms(Graphics2D g2, Form[] forms, ZoomableCanvas.ViewState viewState)
	{
		EditorView.drawForms(g2, forms, viewState);
	}
	public static void drawForms(Graphics2D g2, Iterable<Form> forms, ZoomableCanvas.ViewState viewState)
	{
		EditorView.drawForms(g2, forms, viewState);
	}

	public static Form.Factory createFormFactory()
	{
		return new LineForm.Factory();
	}

	public static Vector<Form> copy(Vector<Form> forms)
	{
		List<Form> list = forms.stream().map(f -> LineForm.convert(LineForm.clone(LineForm.convert(f)))).toList();
		return new Vector<>(list);
	}

	public static class GuideLinesStorage
	{
        private final Vector<GuideLine> guideLines;
        
        public GuideLinesStorage()
        {
        	guideLines = new Vector<>();        	
        }
        public GuideLinesStorage(GuideLinesStorage other)
        {
        	this();
        	add(other);
        }

		public void replace(GuideLinesStorage other)
		{
			guideLines.clear();
			add(other);
		}
		
		public void add(GuideLinesStorage other)
		{
        	for (GuideLine gl : other.guideLines)
        		guideLines.add(new GuideLine(gl));
		}
		
		public boolean isEmpty()
		{
			return guideLines.isEmpty();
		}

		public void writeToFile(PrintWriter out)
		{
			for (GuideLine gl:guideLines)
				out.printf("GuideLine.%s=%s%n", gl.type.name(), Double.toString(gl.pos));
		}

		public void parseLine(String line)
		{
			for (GuideLine.Type type:GuideLine.Type.values()) {
				// out.printf("GuideLine.%s=%s%n", gl.type.toString(), Float.toString(gl.pos));
				String prefix = String.format("GuideLine.%s=", type.name());
				if (line.startsWith(prefix)) {
					String str = line.substring(prefix.length());
					try {
						double pos = Double.parseDouble(str);
						guideLines.add(new GuideLine(type, pos));
					} catch (NumberFormatException e) {
						System.err.printf("Can't convert \"%s\" in line \"%s\" into a numeric value.", str, line);
					}
					break;
				}
			}
		}

		public void setDefaultGuideLines(double[] vertical, double[] horizontal)
		{
    		guideLines.clear();
    		for (double pos : vertical  ) guideLines.add(new GuideLine(GuideLine.Type.Vertical  , pos));
    		for (double pos : horizontal) guideLines.add(new GuideLine(GuideLine.Type.Horizontal, pos));
		}
	}

	private static class GeneralOptionPanel extends JTabbedPane {
		private static final long serialVersionUID = -2024771038202756837L;
		
		private final FormsPanel formsPanel;
		private final GuideLinesPanel guideLinesPanel;

		private Context context;
		
		GeneralOptionPanel(Context context) {
			super();
			this.context = context;
			setBorder(BorderFactory.createTitledBorder("General"));
			addTab("Forms"      ,      formsPanel = new      FormsPanel());
			addTab("Guide Lines", guideLinesPanel = new GuideLinesPanel());
		}
		
		void setGuideLines(Vector<GuideLine> guideLines) {
			guideLinesPanel.setGuideLines(guideLines);
		}
	
		void setSelectedForms(int[] selectedIndices) {
			formsPanel.setSelected(selectedIndices);
		}
	
		void setForms(LineForm<?>[] forms) {
			formsPanel.setForms(forms);
		}

		private <V extends Number> V showNumberInputDialog(Component parentComp, String message, V initialValue, NumberParser<V> parser) {
			String newStr = JOptionPane.showInputDialog(parentComp, message, initialValue);
			if (newStr==null) return null;
			
			try {
				return parser.parseNumber(newStr);
			} catch (NumberFormatException e) {
				message = String.format("Can't parse \"%s\" as numeric value.", newStr);
				JOptionPane.showMessageDialog(parentComp, message, "Wrong input", JOptionPane.ERROR_MESSAGE);
			}
			
			return null;
		}
		
		interface NumberParser<V extends Number>
		{
			V parseNumber(String str) throws NumberFormatException;
		}

		private Double showDoubleInputDialog(Component parentComp, String message, Double initialValue) {
			return showNumberInputDialog(parentComp, message, initialValue, Double::parseDouble);
		}

		private Float showFloatInputDialog(Component parentComp, String message, Float initialValue) {
			return showNumberInputDialog(parentComp, message, initialValue, Float::parseFloat);
		}

		private <V> V showMultipleChoiceDialog(Component parentComp, String message, String title, V[] selectionValues, V initialSelectionValue, Class<V> classObj) {
			Object result = JOptionPane.showInputDialog(parentComp, message, title, JOptionPane.QUESTION_MESSAGE, null, selectionValues, initialSelectionValue);
			if (result==null) return null;
			if (!classObj.isAssignableFrom(result.getClass())) return null;
			return classObj.cast(result);
		}
		
		interface Context {
			boolean canCreateNewForm();
			void addForm (LineForm<?> form);
			void addForms(Vector<LineForm<?>> forms);
			void removeForms(List<LineForm<?>> forms);
			boolean canCreateNewGuideLine();
			void addGuideLine(GuideLine guideLine);
			void removeGuideLine(int index);
			void changeHighlightedForms(List<LineForm<?>> forms);
			void changeSelectedForm(LineForm<?> form);
			void changeHighlightedGuideLine(GuideLine guideLine);
			void repaintView();
			Rectangle2D.Float getViewRectangle();
			void guideLineChanged();
		}
	
		enum FormsPanelButtons { New,Edit,Remove,Copy,Paste,Mirror,Translate }
		
		private class FormsPanel extends JPanel {
			private static final long serialVersionUID = 5266768936706086790L;
			private final JList<LineForm<?>> formList;
			private final Vector<LineForm<?>> localClipboard;
			private Disabler<FormsPanelButtons> disabler;
			
			FormsPanel() {
				super(new BorderLayout(3,3));
				setBorder(BorderFactory.createEmptyBorder(3,3,3,3));
				
				localClipboard = new Vector<>();
				
				disabler = new Disabler<FormsPanelButtons>();
				disabler.setCareFor(FormsPanelButtons.values());
				
				formList = new JList<>();
				formList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
				formList.addListSelectionListener(e->{
					List<LineForm<?>> selectedValues = formList.getSelectedValuesList();
					context.changeHighlightedForms(selectedValues);
					setButtonsEnabled(selectedValues.size());
				});
				
				JScrollPane formListScrollPane = new JScrollPane(formList);
				
				JPanel buttonPanel1 = new JPanel(new GridBagLayout());
				buttonPanel1.add( addToDisabler( disabler, FormsPanelButtons.New   , createButton("New"   , false, e->context.addForm(createNewForm())                       ) ) );
				buttonPanel1.add( addToDisabler( disabler, FormsPanelButtons.Edit  , createButton("Edit"  , false, e->context.changeSelectedForm(formList.getSelectedValue())) ) );
				buttonPanel1.add( addToDisabler( disabler, FormsPanelButtons.Remove, createButton("Remove", false, e->context.removeForms(formList.getSelectedValuesList())  ) ) );
				
				JPanel buttonPanel2 = new JPanel(new GridBagLayout());
				buttonPanel2.add( addToDisabler( disabler, FormsPanelButtons.Copy  , createButton("Copy" , false, e->copyForms(formList.getSelectedValuesList())) ) );
				buttonPanel2.add( addToDisabler( disabler, FormsPanelButtons.Paste , createButton("Paste", false, e->pasteForms()                               ) ) );
				
				JPanel buttonPanel3 = new JPanel(new GridBagLayout());
				buttonPanel3.add( addToDisabler( disabler, FormsPanelButtons.Mirror   , createButton("Mirror"   , false, e->mirrorForms   (formList.getSelectedValuesList()) ) ) );
				buttonPanel3.add( addToDisabler( disabler, FormsPanelButtons.Translate, createButton("Translate", false, e->translateForms(formList.getSelectedValuesList()) ) ) );
				
				JPanel buttonGroupsPanel = new JPanel(new GridLayout(0,1));
				buttonGroupsPanel.add(buttonPanel1);
				buttonGroupsPanel.add(buttonPanel2);
				buttonGroupsPanel.add(buttonPanel3);
				
				add(formListScrollPane,BorderLayout.CENTER);
				add(buttonGroupsPanel,BorderLayout.SOUTH);
			}
	
			private void translateForms(List<LineForm<?>> forms) {
				Float x = showFloatInputDialog(this, "Set X translation value: ", null);
				if (x==null) return;
				Float y = showFloatInputDialog(this, "Set Y translation value: ", null);
				if (y==null) return;
				
				for (LineForm<?> form:forms)
					if (form!=null)
						form.translate(x,y);
				
				context.repaintView();
			}

			private void mirrorForms(List<LineForm<?>> forms) {
				LineForm.MirrorDirection dir = showMultipleChoiceDialog(this, "Select mirror direction:", "Mirror Direction", LineForm.MirrorDirection.values(), null, LineForm.MirrorDirection.class);
				if (dir==null) return;
				Float pos = showFloatInputDialog(this, String.format("Set position of %s mirror axis: ", dir.axisPos.toLowerCase()), null);
				if (pos==null) return;
				
				for (LineForm<?> form:forms)
					if (form!=null)
						form.mirror(dir,pos);
				
				context.repaintView();
			}

			private void pasteForms() {
				Vector<LineForm<?>> vec = new Vector<>();
				for (LineForm<?> form:localClipboard)
					if (form!=null)
						vec.add(LineForm.clone(form));
				context.addForms(vec);
			}

			private void copyForms(List<LineForm<?>> forms) {
				localClipboard.clear();
				for (LineForm<?> form:forms)
					if (form!=null)
						localClipboard.add(LineForm.clone(form));
				setButtonsEnabled(formList.getSelectedValuesList().size());
			}

			private void setButtonsEnabled(int selection) {
				disabler.setEnable(button->{
					switch (button) {
					case New: return context.canCreateNewForm();
					case Edit: return selection==1;
					case Copy:
					case Remove:
					case Mirror:
					case Translate: return selection>0;
					case Paste: return !localClipboard.isEmpty();
					}
					return false;
				});
			}

			private LineForm<?> createNewForm() {
				FormType formType = showMultipleChoiceDialog(this, "Select type of new form:", "Form Type", LineForm.FormType.values(), null, LineForm.FormType.class);
				if (formType==null) return null;
				return LineForm.createNew(formType, context.getViewRectangle());
			}

			void setSelected(int[] selectedIndices) {
				if (selectedIndices==null || selectedIndices.length==0) formList.clearSelection();
				else formList.setSelectedIndices(selectedIndices);
				setButtonsEnabled(selectedIndices==null ? 0 : selectedIndices.length);
			}
	
			void setForms(LineForm<?>[] forms) {
				formList.setModel(new FormListModel(forms));
				disabler.setEnable(FormsPanelButtons.New, context.canCreateNewForm());
			}
	
			private final class FormListModel implements ListModel<LineForm<?>> {
				private LineForm<?>[] forms;
				private Vector<ListDataListener> listDataListeners;
				
				public FormListModel(LineForm<?>[] forms) {
					this.forms = forms;
					listDataListeners = new Vector<>();
				}
	
				@Override public int getSize() { return forms==null ? 0 : forms.length; }
				@Override public LineForm<?> getElementAt(int index) {
					if (forms==null || index<0 || index>=forms.length) return null;
					return forms[index];
				}
			
				@Override public void    addListDataListener(ListDataListener l) { listDataListeners.   add(l); }
				@Override public void removeListDataListener(ListDataListener l) { listDataListeners.remove(l);}
			}
			
		}
		
		private class GuideLinesPanel extends JPanel {
			private static final long serialVersionUID = -2804258616332267816L;
			private final JList<GuideLine> guideLineList;
			private final JButton btnNew;
			private final JButton btnEdit;
			private final JButton btnRemove;
			private GuideLine selectedGuideLine;
			private int selectedIndex;
	
			GuideLinesPanel() {
				super(new BorderLayout(3,3));
				setBorder(BorderFactory.createEmptyBorder(3,3,3,3));
				selectedGuideLine = null;
				selectedIndex = -1;
				
				guideLineList = new JList<GuideLine>();
				guideLineList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
				guideLineList.addListSelectionListener(e->{
					selectedGuideLine = guideLineList.getSelectedValue();
					selectedIndex = guideLineList.getSelectedIndex();
					updateButtons();
					context.changeHighlightedGuideLine(selectedGuideLine);
				});
				
				JScrollPane guideLineListScrollPane = new JScrollPane(guideLineList);
				
				JPanel buttonPanel = new JPanel(new GridBagLayout());
				buttonPanel.add(btnNew    = createButton("New"   , true , e->context.addGuideLine(createNewGuideLine())));
				buttonPanel.add(btnEdit   = createButton("Edit"  , false, e->editGuideLine(selectedGuideLine)));
				buttonPanel.add(btnRemove = createButton("Remove", false, e->context.removeGuideLine(selectedIndex)));
				
				add(guideLineListScrollPane,BorderLayout.CENTER);
				add(buttonPanel,BorderLayout.SOUTH);
				updateButtons();
			}
	
			private void updateButtons() {
				btnNew   .setEnabled(context.canCreateNewGuideLine());
				btnEdit  .setEnabled(selectedGuideLine!=null);
				btnRemove.setEnabled(selectedGuideLine!=null);
			}

			private GuideLine createNewGuideLine() {
				GuideLine.Type type = getGuideLineType();
				if (type==null) return null;
				
				Double pos = getPosOfGuideLine(type, null);
				if (pos==null) return null;
				
				return new GuideLine(type, pos);
			}
			
			private void editGuideLine(GuideLine selected) {
				if (selected==null) return;
				
				Double pos = getPosOfGuideLine(selected.type, selected.pos);
				if (pos==null) return;
				
				selected.pos = pos;
				context.repaintView();
				context.guideLineChanged();
				guideLineList.repaint();
			}
			
			private GuideLine.Type getGuideLineType() {
				return showMultipleChoiceDialog(this, "Select type of new GuideLine:", "GuideLine Type", GuideLine.Type.values(), null, GuideLine.Type.class);
			}
	
			private Double getPosOfGuideLine(GuideLine.Type type, Double initialPos) {
				return showDoubleInputDialog(this,String.format("Set %s position of %s guideline:", type.axis, type.toString().toLowerCase()), initialPos);
			}

			void setGuideLines(Vector<GuideLine> guideLines) {
				guideLineList.setModel(new GuideLineListModel(guideLines));
				updateButtons();
			}
			
			private final class GuideLineListModel implements ListModel<GuideLine> {
				private Vector<ListDataListener> listDataListeners;
				private Vector<GuideLine> guideLines;
	
				public GuideLineListModel(Vector<GuideLine> guideLines) {
					this.guideLines = guideLines;
					listDataListeners = new Vector<>();
				}
	
				@Override public int getSize() { return guideLines==null ? 0 : guideLines.size(); }
				@Override public GuideLine getElementAt(int index) {
					if (guideLines==null || index<0 || index>=guideLines.size()) return null;
					return guideLines.get(index);
				}
			
				@Override public void    addListDataListener(ListDataListener l) { listDataListeners.   add(l); }
				@Override public void removeListDataListener(ListDataListener l) { listDataListeners.remove(l);}
			}
		}
	}
	
	private static class EditorViewContextMenu extends JPopupMenu {
		
		private static final long serialVersionUID = 1271594755142232548L;
		private final JCheckBoxMenuItem miStickToGuideLines;
		private final JCheckBoxMenuItem miStickToFormPoints;
		private final EditorView editorView;
		private final EditorViewFeature[] features;
	
		public EditorViewContextMenu(EditorView editorView, EditorViewFeature[] features) {
			this.editorView = editorView;
			this.features = features;
			add(miStickToGuideLines = createCheckBoxMI("Stick to GuideLines" , editorView.isStickToGuideLines(), editorView::setStickToGuideLines));
			add(miStickToFormPoints = createCheckBoxMI("Stick to Form Points", editorView.isStickToFormPoints(), editorView::setStickToFormPoints));
			for (EditorViewFeature feature : this.features)
				feature.addToEditorViewContextMenu(this);
		}
	
		public void prepareToShow() {
			miStickToGuideLines.setSelected(editorView.isStickToGuideLines());
			miStickToFormPoints.setSelected(editorView.isStickToFormPoints());
			for (EditorViewFeature feature : this.features)
				feature.prepareContextMenuToShow();
		}
		
	}
}
