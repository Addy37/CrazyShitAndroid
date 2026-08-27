package com.webapp.crazyshit;

import android.content.Context;

import java.io.IOException;

/** Routes a page to the matching first-party parser without changing the shared player contract. */
final class PlayableSourceRouter {
    private PlayableSourceRouter() {
    }

    static CrazyShitRepository.StreamInfo resolve(Context context, String pageUrl) throws IOException {
        if (EfuktRepository.isEfuktUrl(pageUrl)) {
            return new EfuktRepository().resolvePlayable(context, pageUrl);
        }
        return new CrazyShitRepository().resolvePlayable(context, pageUrl);
    }
}
