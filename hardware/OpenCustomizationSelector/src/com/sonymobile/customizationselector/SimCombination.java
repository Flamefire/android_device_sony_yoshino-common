package com.sonymobile.customizationselector;

public final class SimCombination {

    private String mGid1 = null;
    private String mGid2 = null;
    private String mIMSI = null;
    private String mMCC = null;
    private String mMNC = null;
    private String mSP = null;
    private String mSimConfigId = null;

    public SimCombination() {
    }

    public String getGid1() {
        return mGid1;
    }

    public String getGid2() {
        return mGid2;
    }

    public String getIMSI() {
        return mIMSI;
    }

    public String getMCC() {
        return mMCC;
    }

    public String getMNC() {
        return mMNC;
    }

    public String getServiceProvider() {
        return mSP;
    }

    public String getSimConfigId() {
        return mSimConfigId;
    }

    public void setGid1(String gid1) {
        mGid1 = gid1;
    }

    public void setGid2(String gid2) {
        mGid2 = gid2;
    }

    public void setIMSI(String imsi) {
        mIMSI = imsi;
    }

    public void setMCC(String mcc) {
        mMCC = mcc;
    }

    public void setMNC(String mnc) {
        mMNC = mnc;
    }

    public void setServiceProvider(String sp) {
        mSP = sp;
    }

    public void setSimConfigId(String configId) {
        mSimConfigId = configId;
    }
}
