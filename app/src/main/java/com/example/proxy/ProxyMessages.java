package com.example.proxy;

import com.example.R;
import com.example.proxy.core.ProxyTester;

/** Plain-language explanations of a proxy test result. */
public final class ProxyMessages {
    private ProxyMessages() {
    }

    /** String resource for the result; a few of them take the app name as %1$s. */
    public static int messageRes(ProxyTester.Result r) {
        switch (r) {
            case OK:
                return R.string.proxy_result_ok;
            case PROXY_NOT_RUNNING:
                return R.string.proxy_result_not_running;
            case PROXY_TIMEOUT:
                return R.string.proxy_result_timeout;
            case NOT_A_PROXY:
                return R.string.proxy_result_not_a_proxy;
            case LOGIN_REQUIRED:
                return R.string.proxy_result_login_required;
            case LOGIN_FAILED:
                return R.string.proxy_result_login_failed;
            default:
                return R.string.proxy_result_no_internet;
        }
    }
}
