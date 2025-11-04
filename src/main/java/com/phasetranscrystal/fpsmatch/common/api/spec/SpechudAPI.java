
package com.phasetranscrystal.fpsmatch.common.api.spec;

public final class SpechudAPI {
    private static IStyleProvider STYLE = IStyleProvider.DEFAULT;
    public static void setStyleProvider(IStyleProvider p){ STYLE = p==null?IStyleProvider.DEFAULT:p; }
    public static IStyleProvider style(){ return STYLE; }
    private SpechudAPI(){}
}