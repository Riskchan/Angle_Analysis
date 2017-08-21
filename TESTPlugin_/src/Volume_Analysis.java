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
	Button m_bt_calc;					// calculate volume
	Button m_bt_set, m_bt_reset;		// set/reset button
	TextField m_txt_low, m_txt_high;	// higher/lower thresholds
	TextField m_min_area;				// ignore area less than this
	TextField m_z_px;					// z-pixels
	
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
	
	// Check if the value in the roi is within the range 
	// Returns true if inside is within the range
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

	// Combine Rois (returns null if rois are empty)
	private Roi combineRoi(ArrayList<Roi> rois){
		if(rois.size() > 1){
	    	ShapeRoi roi1 = new ShapeRoi(rois.get(0));
	    	for(int i=1; i<rois.size(); i++){
	    		ShapeRoi roi2 = new ShapeRoi(rois.get(i));
	    		roi1 = roi1.or(roi2);
	    	}
	    	return roi1;			
		}else{
			return null;
		}
	}
	
	private void findNextRois(ArrayList<ArrayList<Roi>> roimap, int cur_slice, int step){
        ImagePlus imp = IJ.getImage();
        if (null == imp) return;
		ImageProcessor ip = imp.getProcessor();

        // obtain rois at current slice
        ArrayList<Roi> cur_rois = roimap.get(cur_slice);
        
		// Go to the next slice
    	int next_slice = cur_slice + step;
        imp.setPosition(next_slice);

		// Select all
        ThresholdToSelection th = new ThresholdToSelection();
        Roi throi = th.convert(ip);
        if (null == throi) return;
        ShapeRoi shroi = new ShapeRoi(throi);
        Roi[] all_rois = shroi.getRois();

        ArrayList<Roi> next_rois = new ArrayList<Roi>(roimap.get(next_slice));
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
        /*
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
        */

        // Combine ROIs
        Roi next_roi = combineRoi(next_rois);
        if(next_roi != null){
            next_rois = new ArrayList<Roi>();
            next_rois.add(next_roi);
        }

        roimap.set(next_slice, next_rois);
	}
	
	private void volume_analysis() {
		ImagePlus imp = IJ.getImage();
        if (null == imp) return;
		ImageProcessor ip = imp.getProcessor();

		// Roi manager
    	RoiManager manager = RoiManager.getInstance();
    	if (manager == null)
    		manager = new RoiManager();

		// Stack
        int Num = imp.getStackSize();
        int cur_slice = imp.getCurrentSlice();

        // Initialize ROI map
        ArrayList<ArrayList<Roi>> roimap = new ArrayList<ArrayList<Roi>>();
        for(int i=0; i<=Num; i++){
        	ArrayList<Roi> roi_arry = new ArrayList<Roi>();
        	roimap.add(roi_arry);
        }

        // Add current roi to ROI map
        ArrayList<Roi> curroi = new ArrayList<Roi>();
        curroi.add(imp.getRoi());
        roimap.set(cur_slice, curroi);
        
        // Current slice to top
        for(int i=cur_slice; i<Num; i++){
        	findNextRois(roimap, i, 1);
        }

        // Current slice to bottom
        for(int i=cur_slice; i>1; i--){
        	findNextRois(roimap, i, -1);
        }

    	// Forward search
        for(int i=1; i<Num; i++){
        	findNextRois(roimap, i, 1);
        }
    	
        // Backward search
        for(int i=Num-1; i>1; i--){
        	findNextRois(roimap, i, -1);
        }

        for(int i=1; i<roimap.size(); i++){
            imp.setPosition(i);
    		ArrayList<Roi> rois = roimap.get(i);
    		for(int j=0; j<rois.size(); j++){
    			manager.addRoi(rois.get(j));
    		}
    	}
        imp.setPosition(cur_slice);
	}
	
	// Calculate volume
	private void calc_volume(){
		ImagePlus imp = IJ.getImage();
        if (null == imp) return;
		ImageProcessor ip = imp.getProcessor();

        int cur_slice = imp.getCurrentSlice();
		
		// Roi manager
    	RoiManager manager = RoiManager.getInstance();
    	if (manager == null)
    		manager = new RoiManager();

		double z_px = Double.parseDouble(m_z_px.getText());
    	
    	int vol = 0;
    	Roi[] rois = manager.getRoisAsArray();
    	for(int i=0; i<rois.length; i++){
    		int z = manager.getSliceNumber(manager.getName(i));
    		imp.setPosition(z);
    		
    		int vol_roi = 0;
            ShapeRoi shroi = new ShapeRoi(rois[i]);
            Roi[] all_rois = shroi.getRois();
    		for(int j=0; j<all_rois.length; j++){
    			int sign = checkInside(all_rois[j], ip) ? +1 : -1;
    			vol_roi += getRoiArea(all_rois[j]) * sign;
    		}
    		IJ.log(String.valueOf(vol_roi));
    		vol += vol_roi*z_px;
    	}
    	IJ.log("Total volume = " + vol);
        imp.setPosition(cur_slice);
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
    	}else if (src.equals(m_bt_calc)){
    		calc_volume();
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
    	frm.setSize(new Dimension(300,200));
    	frm.setLayout(new GridLayout(0, 1));
        frm.addWindowListener(new WindowAdapter() {
        	public void windowClosing(WindowEvent e) {
        		System.exit(0);
        	}
        });
        
        // Minimum area
        m_z_px = new TextField("1");
		addLabeledComponent("z-pixels:", frm, m_z_px);

        // Minimum area
        m_min_area = new TextField("0");
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

		// Calculate button
		p = new Panel();
		p.setLayout(new GridLayout(1, 1));
		m_bt_calc = new Button("Calculate volume");
		m_bt_calc.addActionListener(this);
		p.add(m_bt_calc);
		frm.add(p);

		// Button for debugging purpose
		/*
		p = new Panel();
		p.setLayout(new GridLayout(1, 1));
		m_bt_debug = new Button("Debug");
		m_bt_debug.addActionListener(this);
		p.add(m_bt_debug);
		frm.add(p);
		*/
		
		// Show
		frm.show();
    }

}
