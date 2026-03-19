package vista;

import java.awt.*;

public class CardPanel extends RoundedBackgroundPanel {
    private Insets borderInsets;
    private Color borderColor;
    private float borderThickness;

    public CardPanel(){
        this(new BorderLayout(), new Insets(8, 8, 8, 8));
    }

    public CardPanel(LayoutManager layout){
        this(layout, new Insets(8, 8, 8, 8));
    }

    public CardPanel(LayoutManager layout, Insets borderInsets){
        this(layout, borderInsets, null, 1f);
    }

    public CardPanel(LayoutManager layout, Insets borderInsets, Color borderColor, float borderThickness){
        super(AppTheme.getPanelBackground(), AppTheme.getArc());
        this.borderInsets = borderInsets;
        this.borderColor = borderColor;
        this.borderThickness = borderThickness;
        setLayout(layout != null ? layout : new BorderLayout());
        refreshCardStyle();
    }

    public void setBorderInsets(Insets borderInsets){
        this.borderInsets = borderInsets;
        refreshBorder();
    }

    public void setBorderColor(Color borderColor){
        this.borderColor = borderColor;
        refreshBorder();
    }

    public void setBorderThickness(float borderThickness){
        this.borderThickness = borderThickness;
        refreshBorder();
    }

    @Override
    public void updateUI(){
        super.updateUI();
        refreshCardStyle();
    }

    private void refreshCardStyle(){
        setBackground(AppTheme.getPanelBackground());
        setArc(AppTheme.getArc());
        refreshBorder();
    }

    private void refreshBorder(){
        Insets resolvedInsets = borderInsets != null ? borderInsets : new Insets(8, 8, 8, 8);
        Color resolvedBorder = borderColor != null ? borderColor : AppTheme.getBorderColor();
        float resolvedThickness = borderThickness > 0f ? borderThickness : 1f;
        setBorder(AppTheme.createRoundedBorder(resolvedInsets, resolvedBorder, resolvedThickness));
    }
}
