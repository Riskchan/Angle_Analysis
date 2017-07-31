import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import java.awt.List;
import java.awt.geom.*;
import ij.plugin.*;
import ij.plugin.frame.*;
import ij.plugin.filter.*;
import java.awt.event.*;
import java.util.*;

public class Volume_Analysis implements PlugIn, ActionListener, KeyListener{
	// Member variables
	Button m_bt_debug;					// button for debugging
	Button m_bt_run;					// run button
	Button m_bt_set, m_bt_reset;		// set/reset button
	TextField m_txt_low, m_txt_high;	// higher/lower thresholds
	TextField m_min_area;				// ignore area less than this
	
	// Get area of roi
	private int getRoiArea(Roi roi){
		int area = 0;
		for (Point p : roi.getContainedPoints()) {
			area++; 
		}
		return area;
	}
	
	// Check overlapping between Roi1 and Roi2
	private boolean checkOverlapping(Roi roi1, Roi roi2){
		Area area = new Area(roi1.getPolygon());
		area.intersect(new Area(roi2.getPolygon()));
		return !area.isEmpty();
	}
	
	// Obtain roi at slice 
	private ArrayList<Roi> getRoisAtSlice(RoiManager manager, int slice){
		ArrayList<Roi> roi_arry = new ArrayList<Roi>();

		List labels = manager.getList();
    	for (int i = 0; i < labels.getItemCount(); i++) {
    		String label = labels.getItem(i);
    		if(manager.getSliceNumber(label) == slice){
    			roi_arry.add(manager.getRoi(i));
    		}
    	}        
    	return roi_arry;
	}

	// Check if the value in the roi is within the range
	private boolean checkInside(Roi roi, ImageProcessor ip){
    	// Thresholds
		int low = Integer.parseInt(m_txt_low.getText());
		int high = Integer.parseInt(m_txt_high.getText());

		boolean isinside = false;
		for (Point p : roi.getContainedPoints()) {
			float val = ip.getf(p.x, p.y);
			if(low < val && val < high)	isinside = true;
		}
		return isinside;
	}

	// Combine Rois and register to the Roi manager
	private void combineRoi(ArrayList<Roi> rois, RoiManager manager){
        if(rois.size() == 1){
        	manager.addRoi(rois.get(0));
        }else if(rois.size() > 1){
        	ShapeRoi roi1 = new ShapeRoi(rois.get(0));
        	for(int i=1; i<rois.size(); i++){
        		ShapeRoi roi2 = new ShapeRoi(rois.get(i));
        		roi1 = roi1.or(roi2);
        	}
        	manager.addRoi(roi1);
        }		
	}
	
	private void findNextRois(int cur_slice, RoiManager manager, int step){
        ImagePlus imp = IJ.getImage();
        if (null == imp) return;
		ImageProcessor ip = imp.getProcessor();

		// Go to the next slice
    	int next_slice = cur_slice + step;
        imp.setPosition(next_slice);

        // obtain rois at current slice
        ArrayList<Roi> cur_rois = getRoisAtSlice(manager, cur_slice);
        
		// Select all
        ThresholdToSelection th = new ThresholdToSelection();
        ShapeRoi shroi = new ShapeRoi(th.run(imp));
        Roi[] all_rois = shroi.getRois();

        ArrayList<Roi> next_rois = new ArrayList<Roi>();
        // Find overlapped rois with current rois
		int min_area = Integer.parseInt(m_min_area.getText());
        for(int i=0; i<all_rois.length; i++){
        	// Pickup higher than minimum area roi
        	if(getRoiArea(all_rois[i]) > min_area){
        		// Pickup roi overlapped with current roi
        		for(int j=0; j<cur_rois.size(); j++){
	        		if(checkOverlapping(cur_rois.get(j), all_rois[i])){
	        			next_rois.add(all_rois[i]);
	        		}
        		}
        	}
        }

        // Find +/- Roi
        ArrayList<Roi> pos_roi = new ArrayList<Roi>();
        ArrayList<Roi> neg_roi = new ArrayList<Roi>();
        for(int i=0; i<next_rois.size(); i++){
        	Roi next_roi = next_rois.get(i);
        	if(checkInside(next_roi, ip)){
        		pos_roi.add(next_roi);
        	}else{
        		neg_roi.add(next_roi);
        	}
        }
        
        // Combine +/- roi
        combineRoi(pos_roi, manager);
        combineRoi(neg_roi, manager);
	}
	
	private void volume_analysis() {
		ImagePlus imp = IJ.getImage();
        if (null == imp) return;
		ImageProcessor ip = imp.getProcessor();

		// Stack
        int Num = imp.getStackSize();
        int cur_slice = imp.getCurrentSlice();

        // get current ROI and ROI manager
        Roi curroi = imp.getRoi();
    	RoiManager manager = RoiManager.getInstance();
    	if (manager == null)
    		manager = new RoiManager();
    	manager.addRoi(curroi);
    	
        // Forward search
        for(int i=cur_slice; i<Num; i++){
        	findNextRois(i, manager, 1);
        }

        //Backward search
        for(int i=cur_slice; i>=1; i--){
        	findNextRois(i, manager, -1);
        }
	}

	// Buttons pressed
    public void actionPerformed(ActionEvent e){
    	// Thresholds
		int low = Integer.parseInt(m_txt_low.getText());
		int high = Integer.parseInt(m_txt_high.getText());
		if (low > high){
			IJ.showMessage("Lower threshold must be lower than Higher threshold.");
			return;
		}
		
		// IJ
		ImagePlus imp = IJ.getImage();
        if (null == imp) return;
		ImageProcessor ip = imp.getProcessor();		

		Object src = e.getSource();
    	if (src==null){
			return;
    	}else if (src.equals(m_bt_run)){
    		volume_analysis();
    	}else if (src.equals(m_bt_set)){
			ip.setThreshold(low, high, ImageProcessor.RED_LUT);
			imp.updateAndDraw();    		
    	}else if (src.equals(m_bt_reset)){
        	ip.resetThreshold();
			imp.updateAndDraw();
    	}else if (src.equals(m_bt_debug)){
            Roi curroi = imp.getRoi();
            checkInside(curroi, ip);
    	}
	}

    // Key pressed
    public void keyPressed(KeyEvent e) {
    	// If Enter pressed...
		if(e.getKeyCode() == e.VK_ENTER){
			// IJ
			ImagePlus imp = IJ.getImage();
	        if (null == imp) return;
			ImageProcessor ip = imp.getProcessor();		

			int low = Integer.parseInt(m_txt_low.getText());
			int high = Integer.parseInt(m_txt_high.getText());
			if (low > high){
				IJ.showMessage("Lower threshold must be lower than Higher threshold.");
				return;
			}

			ip.setThreshold(low, high, ImageProcessor.RED_LUT);
			imp.updateAndDraw();    		    		
    	}
    }
    
    public void keyReleased(KeyEvent e) {
    	// Do nothing
    }
    
    public void keyTyped(KeyEvent e) {
    	// Do nothing
    }
    
    // Adding new label + component
    private void addLabeledComponent(String l, Frame frm, Component c){
        Panel p = new Panel();
		p.setLayout(new GridLayout(0, 2));
		p.add(new Label(l));
        p.add(c);
        frm.add(p);
    }
    
    public void run(String arg) {	
		
        Panel p = new Panel();
    	Frame frm = new Frame(new String("Volume analysis"));
    	frm.setSize(new Dimension(300,150));
    	frm.setLayout(new GridLayout(0, 1));
        frm.addWindowListener(new WindowAdapter() {
        	public void windowClosing(WindowEvent e) {
        		System.exit(0);
        	}
        });
        
        // Minimum area
        m_min_area = new TextField("10");
		addLabeledComponent("Ignore less than (px^2):", frm, m_min_area);

        // Lower/Higher thresholds
        m_txt_low = new TextField("10000");
        addLabeledComponent("Lower threshold level:", frm, m_txt_low);
        m_txt_low.addKeyListener(this);
        
        m_txt_high = new TextField("100000");
		addLabeledComponent("Higher threshold level:", frm, m_txt_high);
        m_txt_high.addKeyListener(this);
        
		// Set/Reset threshold button
        p = new Panel();
		p.setLayout(new GridLayout(0, 2));

		m_bt_set = new Button("Set threshold");
		m_bt_set.addActionListener(this);
		p.add(m_bt_set);

		m_bt_reset = new Button("Reset threshold");
		m_bt_reset.addActionListener(this);
		p.add(m_bt_reset);
		frm.add(p);

		// Run button
		p = new Panel();
		p.setLayout(new GridLayout(1, 1));
		m_bt_run = new Button("Run");
		m_bt_run.addActionListener(this);
		p.add(m_bt_run);
		frm.add(p);

		// Button for debugging purpose
		p = new Panel();
		p.setLayout(new GridLayout(1, 1));
		m_bt_debug = new Button("Debug");
		m_bt_debug.addActionListener(this);
		p.add(m_bt_debug);
		frm.add(p);
		
		// Show
		frm.show();
    }

}
