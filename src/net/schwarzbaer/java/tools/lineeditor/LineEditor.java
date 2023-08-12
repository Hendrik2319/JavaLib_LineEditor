package net.schwarzbaer.java.tools.lineeditor;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;
import java.util.function.BiConsumer;
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
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import net.schwarzbaer.java.lib.gui.GeneralIcons;
import net.schwarzbaer.java.lib.gui.GeneralIcons.GrayCommandIcons;
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
	
	public record FormsChangedEvent(FormsChangedEvent.Type type, String caller, Form[] newFormsList)
	{
		FormsChangedEvent(FormsChangedEvent.Type type, String caller) { this(type, caller, null); }
		public enum Type { Added, Removed, Changed }
	}
	
	public interface Context
	{
		void switchOptionsPanel(JComponent panel);
		boolean canModifyFormsList();
		void guideLinesChanged(GuideLinesChangedEvent event);
		void formsChanged(FormsChangedEvent event);
	}
	
	private LineForm<?>[] lineforms = null;
	
	private final Context context;
	private final EditorView editorView;
	private final GeneralOptionPanel generalOptionPanel;
	private final EditorViewContextMenu editorViewContextMenu;
	private GuideLinesStorage guideLinesStorage;

	public LineEditor(Rectangle2D.Double initialViewRect, Context context, EditorViewFeature... features) {
		this.context = context;
		guideLinesStorage = null;
		
		editorView = new EditorView(initialViewRect, features, new EditorViewContext());
		editorView.setPreferredSize(500, 500);
		editorViewContextMenu = new EditorViewContextMenu(editorView, features);
		
		generalOptionPanel = new GeneralOptionPanel(editorView, new GuideLinesListenerImpl(), new GeneralOptionPanelContext());
		generalOptionPanel.setPreferredSize(new Dimension(200, 200));
	}
	
	private class EditorViewContext implements EditorView.Context
	{
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
	}
	
	private class GuideLinesListenerImpl implements GeneralOptionPanel.GuideLinesListener
	{
		@Override public void intervalAdded  (ListDataEvent e) { guideLinesChanged(GuideLinesChangedEvent.Type.Added  , "GeneralOptionPanel.GuideLinesListener.intervalAdded"  ); }
		@Override public void intervalRemoved(ListDataEvent e) { guideLinesChanged(GuideLinesChangedEvent.Type.Removed, "GeneralOptionPanel.GuideLinesListener.intervalRemoved"); }
		@Override public void contentsChanged(ListDataEvent e) { guideLinesChanged(GuideLinesChangedEvent.Type.Changed, "GeneralOptionPanel.GuideLinesListener.contentsChanged"); }

		private void guideLinesChanged(GuideLinesChangedEvent.Type type, String caller)
		{
			editorView.updateAfterGuideLinesChange();
			context.guideLinesChanged(new GuideLinesChangedEvent(type, caller));
		}
	}

	private class GeneralOptionPanelContext implements GeneralOptionPanel.Context
	{
		@Override
		public boolean canModifyFormsList()
		{
			return context.canModifyFormsList();
		}

		@Override
		public void formsChanged(boolean propagateList) {
			context.formsChanged(
				new FormsChangedEvent(
					FormsChangedEvent.Type.Changed,
					"GeneralOptionPanel.Context.formsChanged",
					propagateList
						? LineForm.convert(lineforms)
						: null
				)
			);
		}

		@Override
		public void addForm(LineForm<?> form) {
			if (form==null) return;
			LineForm<?>[] newArr = lineforms==null ? new LineForm[1] : Arrays.copyOf(lineforms, lineforms.length+1);
			newArr[newArr.length-1] = form;
			setNewArray(newArr, FormsChangedEvent.Type.Added, "GeneralOptionPanel.Context.addForm");
		}
		@Override
		public void addForms(Vector<LineForm<?>> forms) {
			if (forms==null || forms.isEmpty()) return;
			LineForm<?>[] newArr = lineforms==null ? new LineForm[forms.size()] : Arrays.copyOf(lineforms, lineforms.length+forms.size());
			int offset = lineforms==null ? 0 : lineforms.length;
			for (int i=0; i<forms.size(); i++)
				newArr[offset+i] = forms.get(i);
			setNewArray(newArr, FormsChangedEvent.Type.Added, "GeneralOptionPanel.Context.addForms");
		}
		@Override
		public void removeForms(List<LineForm<?>> forms) {
			if (forms==null || forms.isEmpty()) return;
			Vector<LineForm<?>> vec = new Vector<>(Arrays.asList(lineforms));
			for (LineForm<?> rf:forms) vec.remove(rf);
			LineForm<?>[] newArr = vec.toArray(new LineForm<?>[vec.size()]);
			setNewArray(newArr, FormsChangedEvent.Type.Removed, "GeneralOptionPanel.Context.removeForms");
		}

		private void setNewArray(LineForm<?>[] newArr, FormsChangedEvent.Type eventType, String caller)
		{
			lineforms = newArr;
			editorView        .setForms(lineforms);
			generalOptionPanel.setForms(lineforms);
			if (!context.canModifyFormsList()) throw new IllegalStateException();
			context.formsChanged(new FormsChangedEvent(eventType, caller, LineForm.convert(lineforms)));
		}
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

	static JToggleButton createToggleButton(String title, ButtonGroup bg, boolean selected, ActionListener al) {
		JToggleButton comp = new JToggleButton(title,selected);
		if (bg!=null) bg.add(comp);
		if (al!=null) comp.addActionListener(al);
		return comp;
	}

	static JButton createButton(String title, ActionListener al) {
		return createButton(title, null, true, al);
	}
	static JButton createButton(String title, boolean enabled, ActionListener al) {
		return createButton(title, null, enabled, al);
	}
	static JButton createButton(String title, GeneralIcons.IconGroup icons, boolean enabled, ActionListener al) {
		JButton comp = new JButton(title);
		comp.setEnabled(enabled);
		if (al!=null) comp.addActionListener(al);
		if (icons!=null) { comp.setIcon(icons.getEnabledIcon()); comp.setDisabledIcon(icons.getDisabledIcon()); }
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
		editorView        .setForms(lineforms);
		generalOptionPanel.setForms(lineforms);
	}
	
	public void setGuideLines(GuideLinesStorage guideLinesStorage)
	{
		this.guideLinesStorage = guideLinesStorage;
		editorView        .setGuideLines(this.guideLinesStorage);
		generalOptionPanel.setGuideLines(this.guideLinesStorage);
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
        final Vector<GuideLine> guideLines;
        
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

		interface Context
		{
			boolean canModifyFormsList();
			void formsChanged(boolean propagateList);
			void addForm    (       LineForm<?>  form );
			void addForms   (Vector<LineForm<?>> forms);
			void removeForms(List  <LineForm<?>> forms);
		}

		private final Context context;
		
		private final EditorView editorView;
		private final FormsPanel formsPanel;
		private final GuideLinesPanel guideLinesPanel;
		
		GeneralOptionPanel(EditorView editorView, GuideLinesListener guideLinesListener, Context context) {
			super();
			this.editorView = editorView;
			this.context = context;
			setBorder(BorderFactory.createTitledBorder("General"));
			addTab("Forms"      ,      formsPanel = new      FormsPanel());
			addTab("Guide Lines", guideLinesPanel = new GuideLinesPanel(guideLinesListener));
		}

		void setGuideLines(GuideLinesStorage guideLinesStorage) {
			guideLinesPanel.setGuideLines(guideLinesStorage);
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

		@SuppressWarnings("unused")
		private Float showFloatInputDialog(Component parentComp, String message, Float initialValue) {
			return showNumberInputDialog(parentComp, message, initialValue, Float::parseFloat);
		}

		private <V> V showMultipleChoiceDialog(Component parentComp, String message, String title, V[] selectionValues, V initialSelectionValue, Class<V> classObj) {
			Object result = JOptionPane.showInputDialog(parentComp, message, title, JOptionPane.QUESTION_MESSAGE, null, selectionValues, initialSelectionValue);
			if (result==null) return null;
			if (!classObj.isAssignableFrom(result.getClass())) return null;
			return classObj.cast(result);
		}
		
		private class FormsPanel extends JPanel {
			private static final long serialVersionUID = 5266768936706086790L;
			private final JList<LineForm<?>> formList;
			private final Vector<LineForm<?>> localClipboard;
			private FormListModel formListModel;
			private final JButton btnNew;
			private final JButton btnEdit;
			private final JButton btnRemove;
			private final JButton btnCopy;
			private final JButton btnPaste;
			private final JButton btnMoveUp;
			private final JButton btnMoveDown;
			private final JButton btnMirror;
			private final JButton btnTranslate;
			private final JButton btnRotateCW;
			private final JButton btnRotateCCW;
			private final JButton btnRotate;
			
			FormsPanel() {
				super(new BorderLayout(3,3));
				setBorder(BorderFactory.createEmptyBorder(3,3,3,3));
				formListModel = null;
				
				localClipboard = new Vector<>();
				
				formList = new JList<>();
				formList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
				formList.addListSelectionListener(e->{
					List<LineForm<?>> selectedValues = formList.getSelectedValuesList();
					editorView.setHighlightedForms(selectedValues);
					updateButtons();
				});
				formList.addMouseListener(new MouseAdapter() {
					@Override public void mouseClicked(MouseEvent e) {
						if (e.getButton()==MouseEvent.BUTTON1 && e.getClickCount()>=2)
							editorView.setSelectedForm(formList.getSelectedValue());
					}
				});
				
				JScrollPane formListScrollPane = new JScrollPane(formList);
				
				GridBagConstraints c = new GridBagConstraints();
				c.fill = GridBagConstraints.BOTH;
				
				JPanel buttonPanel1 = new JPanel(new GridBagLayout());
				buttonPanel1.add( btnNew    = createButton("New"   , GrayCommandIcons.IconGroup.Add   , false, e->context.addForm(createNewForm())                       ), c );
				buttonPanel1.add( btnEdit   = createButton("Edit"  ,                                    false, e->editorView.setSelectedForm(formList.getSelectedValue())), c );
				buttonPanel1.add( btnRemove = createButton("Remove", GrayCommandIcons.IconGroup.Delete, false, e->context.removeForms(formList.getSelectedValuesList())  ), c );
				buttonPanel1.add( btnMoveUp = createButton(null   , GrayCommandIcons.IconGroup.Up   , false, e->{
					int[] selectedIndices = formList.getSelectedIndices();
					if (formListModel==null || selectedIndices.length!=1) return;
					formListModel.move(selectedIndices[0], -1, formList::setSelectedIndex);
					context.formsChanged(true);
				}), c );
				buttonPanel1.add( btnMoveDown = createButton(null, GrayCommandIcons.IconGroup.Down , false, e->{
					int[] selectedIndices = formList.getSelectedIndices();
					if (formListModel==null || selectedIndices.length!=1) return;
					formListModel.move(selectedIndices[0], +1, formList::setSelectedIndex);
					context.formsChanged(true);
				}), c );
				
				JPanel buttonPanel2 = new JPanel(new GridBagLayout());
				buttonPanel2.add( btnCopy   = createButton("Copy" , GrayCommandIcons.IconGroup.Copy , false, e->copyForms(formList.getSelectedValuesList())), c );
				buttonPanel2.add( btnPaste  = createButton("Paste", GrayCommandIcons.IconGroup.Paste, false, e->pasteForms()                               ), c );
				buttonPanel2.add( btnMirror    = createButton("Mirror"    , false, e->mirrorForms   (formList.getSelectedValuesList()) ), c );
				buttonPanel2.add( btnTranslate = createButton("Translate" , false, e->translateForms(formList.getSelectedValuesList()) ), c );
				
				JPanel buttonPanel3 = new JPanel(new GridBagLayout());
				buttonPanel3.add( btnRotateCW  = createButton("90°", GrayCommandIcons.IconGroup.Reload   , false, e->rotateForms90(formList.getSelectedValuesList(), true ) ), c );
				buttonPanel3.add( btnRotateCCW = createButton("90°", GrayCommandIcons.IconGroup.ReloadCCW, false, e->rotateForms90(formList.getSelectedValuesList(), false) ), c );
				buttonPanel3.add( btnRotate    = createButton("...", GrayCommandIcons.IconGroup.ReloadCCW, false, e->rotateForms  (formList.getSelectedValuesList()       ) ), c );
				
				JPanel buttonGroupsPanel = new JPanel(new GridLayout(0,1));
				buttonGroupsPanel.add(buttonPanel1);
				buttonGroupsPanel.add(buttonPanel2);
				buttonGroupsPanel.add(buttonPanel3);
				
				add(formListScrollPane,BorderLayout.CENTER);
				add(buttonGroupsPanel,BorderLayout.SOUTH);
			}
			
			private void rotateForms(List<LineForm<?>> forms)
			{
				Double a = showDoubleInputDialog(this, "Rotation Angle in ° (counterclockwise): ", null);
				if (a==null) return;
				Double x = showDoubleInputDialog(this, "Rotation Center X: ", null);
				if (x==null) return;
				Double y = showDoubleInputDialog(this, "Rotation Center Y: ", null);
				if (y==null) return;
				
				transformForms(forms, form -> form.rotate(x, y, -a*Math.PI/180));
			}
	
			private void rotateForms90(List<LineForm<?>> forms, boolean mathPosDir)
			{
				Double x = showDoubleInputDialog(this, "Rotation Center X: ", null);
				if (x==null) return;
				Double y = showDoubleInputDialog(this, "Rotation Center Y: ", null);
				if (y==null) return;
				
				transformForms(forms, form -> form.rotate90(x, y, mathPosDir));
			}

			private void translateForms(List<LineForm<?>> forms) {
				Double x = showDoubleInputDialog(this, "Set X translation value: ", null);
				if (x==null) return;
				Double y = showDoubleInputDialog(this, "Set Y translation value: ", null);
				if (y==null) return;
				
				transformForms(forms, form -> form.translate(x,y));
			}

			private void mirrorForms(List<LineForm<?>> forms)
			{
				LineForm.MirrorDirection dir = showMultipleChoiceDialog(this, "Select mirror direction:", "Mirror Direction", LineForm.MirrorDirection.values(), null, LineForm.MirrorDirection.class);
				if (dir==null) return;
				Double pos = showDoubleInputDialog(this, String.format("Set position of %s mirror axis: ", dir.axisPos.toLowerCase()), null);
				if (pos==null) return;
				
				transformForms(forms, form -> form.mirror(dir,pos));
			}

			private void transformForms(List<LineForm<?>> forms, Consumer<LineForm<?>> action)
			{
				for (LineForm<?> form:forms)
					if (form!=null)
						action.accept(form);
				
				context.formsChanged(false);
				editorView.repaint();
				formList.repaint();
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
				updateButtons();
			}

			private void updateButtons() {
				int[] selectedIndices = formList.getSelectedIndices();
				btnNew      .setEnabled(context.canModifyFormsList());
				btnEdit     .setEnabled(selectedIndices.length==1);
				btnRemove   .setEnabled(selectedIndices.length>0);
				btnMoveUp   .setEnabled(selectedIndices.length==1 && formListModel!=null && formListModel.canMove(selectedIndices[0],-1));
				btnMoveDown .setEnabled(selectedIndices.length==1 && formListModel!=null && formListModel.canMove(selectedIndices[0],+1));
				
				btnCopy     .setEnabled(selectedIndices.length>0);
				btnPaste    .setEnabled(!localClipboard.isEmpty());
				btnMirror   .setEnabled(selectedIndices.length>0);
				btnTranslate.setEnabled(selectedIndices.length>0);
				
				btnRotateCW .setEnabled(selectedIndices.length>0);
				btnRotateCCW.setEnabled(selectedIndices.length>0);
				btnRotate   .setEnabled(selectedIndices.length>0);
			}

			private LineForm<?> createNewForm() {
				FormType formType = showMultipleChoiceDialog(this, "Select type of new form:", "Form Type", LineForm.FormType.values(), null, LineForm.FormType.class);
				if (formType==null) return null;
				return LineForm.createNew(formType, editorView.getViewRectangle());
			}

			void setSelected(int[] selectedIndices) {
				if (selectedIndices==null || selectedIndices.length==0) formList.clearSelection();
				else formList.setSelectedIndices(selectedIndices);
				updateButtons();
			}
	
			void setForms(LineForm<?>[] forms) {
				formList.setModel(formListModel = new FormListModel(forms));
				updateButtons();
			}
	
			private final class FormListModel extends AbstractListModel<LineForm<?>> {
				private final LineForm<?>[] forms;
				
				public FormListModel(LineForm<?>[] forms) {
					super(null, null, (index1, index2) -> {
						LineForm<?> temp = forms[index1];
						forms[index1] = forms[index2];
						forms[index2] = temp;
					});
					this.forms = forms;
				}
	
				@Override protected boolean hasData() { return forms!=null; }
				@Override public int getSize() { return forms==null ? 0 : forms.length; }
				@Override public LineForm<?> getElementAt(int index) { return isIndexOk(index) ? forms[index] : null; }
			}
			
		}
		
		interface GuideLinesListener extends ListDataListener
		{
			// t.b.d.
		}
		
		private class GuideLinesPanel extends JPanel {
			private static final long serialVersionUID = -2804258616332267816L;
			private final JList<GuideLine> guideLineList;
			private final JButton btnNew;
			private final JButton btnEdit;
			private final JButton btnRemove;
			private final JButton btnMoveUp;
			private final JButton btnMoveDown;
			private GuideLine selectedGuideLine;
			private int selectedIndex;
			private GuideLineListModel guideLineListModel;
			private final GuideLinesListener guideLinesListener;
	
			GuideLinesPanel(GuideLinesListener guideLinesListener) {
				super(new BorderLayout(3,3));
				this.guideLinesListener = guideLinesListener;
				setBorder(BorderFactory.createEmptyBorder(3,3,3,3));
				selectedGuideLine = null;
				selectedIndex = -1;
				guideLineListModel = null;
				
				guideLineList = new JList<GuideLine>();
				guideLineList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
				guideLineList.addListSelectionListener(e->{
					selectedGuideLine = guideLineList.getSelectedValue();
					selectedIndex     = guideLineList.getSelectedIndex();
					updateButtons();
					editorView.setHighlightedGuideLine(selectedGuideLine);
				});
				guideLineList.addMouseListener(new MouseAdapter() {
					@Override public void mouseClicked(MouseEvent e) {
						if (e.getButton()==MouseEvent.BUTTON1 && e.getClickCount()>=2)
							editSelectedGuideLine();
					}
				});
				
				JScrollPane guideLineListScrollPane = new JScrollPane(guideLineList);
				
				JPanel buttonPanel = new JPanel(new GridBagLayout());
				GridBagConstraints c = new GridBagConstraints();
				c.fill = GridBagConstraints.BOTH;
				buttonPanel.add(btnNew = createButton("New", GrayCommandIcons.IconGroup.Add, true , e->{
					if (guideLineListModel!=null)
						guideLineListModel.add(createNewGuideLine(), guideLineList::setSelectedIndex);
				}), c);
				buttonPanel.add(btnEdit = createButton("Edit", false, e->{
					editSelectedGuideLine();
				}), c);
				buttonPanel.add(btnRemove = createButton("Remove", GrayCommandIcons.IconGroup.Delete, false, e-> {
					if (guideLineListModel!=null)
					{
						guideLineListModel.remove(selectedIndex);
						guideLineList.clearSelection();
					}
				}), c);
				buttonPanel.add(btnMoveUp   = createButton(null, GrayCommandIcons.IconGroup.Up, false, e->{
					if (guideLineListModel!=null)
						guideLineListModel.move(selectedIndex, -1, guideLineList::setSelectedIndex);
				}), c);
				buttonPanel.add(btnMoveDown = createButton(null, GrayCommandIcons.IconGroup.Down, false, e->{
					if (guideLineListModel!=null)
						guideLineListModel.move(selectedIndex, +1, guideLineList::setSelectedIndex);
				}), c);
				
				add(guideLineListScrollPane,BorderLayout.CENTER);
				add(buttonPanel,BorderLayout.SOUTH);
				updateButtons();
			}
	
			private void updateButtons() {
				btnNew     .setEnabled(guideLineListModel!=null && guideLineListModel.hasData());
				btnEdit    .setEnabled(selectedGuideLine!=null);
				btnRemove  .setEnabled(selectedGuideLine!=null);
				btnMoveUp  .setEnabled(guideLineListModel!=null && guideLineListModel.canMove(selectedIndex,-1));
				btnMoveDown.setEnabled(guideLineListModel!=null && guideLineListModel.canMove(selectedIndex,+1));
			}

			private GuideLine createNewGuideLine() {
				GuideLine.Type type = getGuideLineType();
				if (type==null) return null;
				
				Double pos = getPosOfGuideLine(type, null);
				if (pos==null) return null;
				
				return new GuideLine(type, pos);
			}
			
			private void editSelectedGuideLine() {
				if (guideLineListModel==null) return;
				if (selectedGuideLine==null) return;
				
				Double pos = getPosOfGuideLine(selectedGuideLine.type, selectedGuideLine.pos);
				if (pos==null) return;
				
				selectedGuideLine.pos = pos;
				editorView.repaint();
			//	context.guideLineChanged();
				guideLineList.repaint();
				guideLineListModel.fireContentsChanged(guideLineListModel, selectedIndex, selectedIndex);
			}
			
			private GuideLine.Type getGuideLineType() {
				return showMultipleChoiceDialog(this, "Select type of new GuideLine:", "GuideLine Type", GuideLine.Type.values(), null, GuideLine.Type.class);
			}
	
			private Double getPosOfGuideLine(GuideLine.Type type, Double initialPos) {
				return showDoubleInputDialog(this,String.format("Set %s position of %s guideline:", type.axis, type.toString().toLowerCase()), initialPos);
			}

			void setGuideLines(GuideLinesStorage guideLinesStorage) {
				guideLineListModel = new GuideLineListModel(guideLinesStorage);
				guideLineListModel.addListDataListener(guideLinesListener);
				guideLineList.setModel(guideLineListModel);
				updateButtons();
			}

			private final class GuideLineListModel extends AbstractListModel<GuideLine>
			{
				private final GuideLinesStorage storage;
				
				GuideLineListModel(GuideLinesStorage storage)
				{
					super(
						storage==null ? null : storage.guideLines::removeElementAt,
						storage==null ? null : storage.guideLines::insertElementAt,
						null
					);
					this.storage = storage;
				}

				void add(GuideLine newGuideLine, Consumer<Integer> updateSelection)
				{
					if (storage!=null)
					{
						storage.guideLines.add(newGuideLine);
						int index = storage.guideLines.size()-1;
						fireIntervalAdded(this, index, index);
						if (updateSelection!=null) updateSelection.accept(index);
					}
				}

				void remove(int index)
				{
					if (!isIndexOk(index)) return;
					storage.guideLines.removeElementAt(index);
					fireIntervalRemoved(this, index, index);
				}

				@Override protected boolean hasData() { return storage!=null; }
				@Override public int getSize() { return storage==null ? 0 : storage.guideLines.size(); }
				@Override public GuideLine  getElementAt(int index) { return isIndexOk(index) ? storage.guideLines.get(index) : null; }
			}
		}
	}
	
	private static abstract class AbstractListModel<ItemType> implements ListModel<ItemType> {
		private final Vector<ListDataListener> listDataListeners;
		private final BiConsumer<Integer, Integer> swap;
	
		AbstractListModel(Consumer<Integer> remove, BiConsumer<ItemType, Integer> insert, BiConsumer<Integer, Integer> swap) {
			this.swap = swap!=null ? swap : remove==null || insert==null ? null : (index1, index2) -> {
				ItemType guideLine = getElementAt(index1);
				remove.accept(index1);
				insert.accept(guideLine, index2);
			};
			listDataListeners = new Vector<>();
		}
		
		protected abstract boolean hasData();
	
		void move(int index, int inc, Consumer<Integer> updateSelection)
		{
			if (!canMove(index, inc)) return;
			swap.accept(index, index+inc);
			fireContentsChanged(this, Math.min(index, index+inc), Math.max(index, index+inc));
			if (updateSelection!=null)
				updateSelection.accept(index+inc);
		}
		
		protected boolean isIndexOk(int index) { return 0<=index && index<getSize(); }
	
		boolean canMove(int index, int inc)
		{
			if (swap==null) return false;
			if (!hasData()) return false;
			if (index    <0 || index    >=getSize()) return false;
			if (index+inc<0 || index+inc>=getSize()) return false;
			return true;
		}
	
		@Override public void    addListDataListener(ListDataListener l) { listDataListeners.   add(l); }
		@Override public void removeListDataListener(ListDataListener l) { listDataListeners.remove(l);}
		
		void fireContentsChanged(Object source, int startIndex, int endIndex) {
			ListDataEvent e = new ListDataEvent(source, ListDataEvent.CONTENTS_CHANGED, startIndex, endIndex);
			for (ListDataListener l : listDataListeners) l.contentsChanged(e);
		}
		void fireIntervalAdded(Object source, int startIndex, int endIndex) {
			ListDataEvent e = new ListDataEvent(source, ListDataEvent.INTERVAL_ADDED, startIndex, endIndex);
			for (ListDataListener l : listDataListeners) l.intervalAdded(e);
		}
		void fireIntervalRemoved(Object source, int startIndex, int endIndex) {
			ListDataEvent e = new ListDataEvent(source, ListDataEvent.INTERVAL_REMOVED, startIndex, endIndex);
			for (ListDataListener l : listDataListeners) l.intervalRemoved(e);
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
