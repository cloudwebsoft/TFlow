package com.cloudweb.oa.service.impl;

import com.cloudweb.oa.api.*;
import com.cloudweb.oa.service.MacroCtlService;
import com.redmoon.oa.flow.macroctl.*;
import org.springframework.stereotype.Service;

@Service
public class MacroCtlServiceImpl implements MacroCtlService {

    @Override
    public INestTableCtl getNestTableCtl() {
        return new NestTableCtl();
    }

    @Override
    public INestSheetCtl getNestSheetCtl() {
        return new NestSheetCtl();
    }

    @Override
    public ISQLCtl getSQLCtl() {
        return null;
    }

    @Override
    public IBasicSelectCtl getBasicSelectCtl() {
        return new BasicSelectCtl();
    }

    @Override
    public IModuleFieldSelectCtl getModuleFieldSelectCtl() {
        return null;
    }

    @Override
    public IBarcodeCtl getBarcodeCtl() {
        return null;
    }

    @Override
    public IFormulaCtl getFormulaCtl() {
        return null;
    }

    @Override
    public IQrcodeCtl getQrcodeCtl() {
        return null;
    }

}
