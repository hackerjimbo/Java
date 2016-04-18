abstract class Mapping
{
    public Mapping (Point inOutMax)
    {
	inMax_ = inOutMax;
	outMax_ = inOutMax;
	before_ = null;
    }

    public Mapping (Point inMax, Point outMax)
    {
	inMax_ = inMax;
	outMax_ = outMax;
	before_ = null;
    }

    public Mapping (Mapping before, Point outMax)
    {
	inMax_ = before.getOutMax ();
	outMax_ = outMax;
	before_ = before;
    }

    public void validateIn (Point p)
    {
	if (p.inside (inMax_))
	    return;

	throw new IllegalArgumentException ("Input co-ordinate " + p + " outside " + inMax_);
    }

    public void validateOut (Point p)
    {
	if (p.inside (outMax_))
	    return;

	throw new IllegalArgumentException ("Output co-ordinate " + p + " outside " + outMax_);
    }

    public Point getInMax ()
    {
	return inMax_;
    }

    public Point getOriginalMax ()
    {
	return (before_ == null) ? inMax_ : before_.getOriginalMax ();
    }

    public Point getOutMax ()
    {
	return outMax_;
    }

    abstract public Point map (Point p);
    abstract public String toString ();

    protected final Point inMax_;
    protected final Point outMax_;
    protected Mapping before_;
}
