package com.example.demo.batch.config;

public class SampleData {

	
    private String c1;
	private String c2;
	private String c3;
	private String c4;
	private String c5;
	private String c6;

    public SampleData() {
    }

    public SampleData(String c1, String c2, String c3, String c4, String c5, String c6) {
        this.c1 = c1;
        this.c2 = c2;
        this.c3 = c3;
        this.c4 = c4;
        this.c5 = c5;
        this.c6 = c6;
    }


    public String getC1() {
		return c1;
	}

	public void setC1(String c1) {
		this.c1 = c1;
	}

	public String getC2() {
		return c2;
	}

	public void setC2(String c2) {
		this.c2 = c2;
	}

	public String getC3() {
		return c3;
	}

	public void setC3(String c3) {
		this.c3 = c3;
	}

	public String getC4() {
		return c4;
	}

	public void setC4(String c4) {
		this.c4 = c4;
	}

	public String getC5() {
		return c5;
	}

	public void setC5(String c5) {
		this.c5 = c5;
	}

	public String getC6() {
		return c6;
	}

	public void setC6(String c6) {
		this.c6 = c6;
	}

	@Override
    public String toString() {
        return "c1: " + c1 + ", c2: " + c2 + ", c3: " + c3 + ", c4: " + c4 + ", c5: " + c5 + ", c6: " + c6;
    }

}
