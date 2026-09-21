package com.webapp.crazyshit;

import android.content.Context;

import java.io.IOException;

/** Routes a page to the matching first-party parser without changing the shared player contract. */
final class PlayableSourceRouter {
    private PlayableSourceRouter() {
    }

    static CrazyShitRepository.StreamInfo resolve(Context context, String pageUrl) throws IOException {
        if (WikiFeetRepository.isWikiFeetUrl(pageUrl)) {
            return new WikiFeetRepository().resolvePlayable(pageUrl);
        }
        if (FapelloRepository.isFapelloUrl(pageUrl)) {
            return new FapelloRepository().resolvePlayable(context, pageUrl);
        }
        if (BunkrRepository.isBunkrUrl(pageUrl)) {
            return new BunkrRepository().resolvePlayable(context, pageUrl);
        }
        if (OnlyHavenRepository.isOnlyHavenUrl(pageUrl)) {
            return new OnlyHavenRepository().resolvePlayable(context, pageUrl);
        }
        WebVideoSourceRepository web = new WebVideoSourceRepository();
        if (WebVideoSourceRepository.isKaoticUrl(pageUrl)) {
            return web.resolvePlayable(context, WebVideoSourceRepository.Source.KAOTIC, pageUrl);
        }
        if (WebVideoSourceRepository.isTheYncUrl(pageUrl)) {
            return web.resolvePlayable(context, WebVideoSourceRepository.Source.THEYNC, pageUrl);
        }
        if (WebVideoSourceRepository.isItemFixUrl(pageUrl)) {
            return web.resolvePlayable(context, WebVideoSourceRepository.Source.ITEMFIX, pageUrl);
        }
        if (EfuktRepository.isEfuktUrl(pageUrl)) {
            return new EfuktRepository().resolvePlayable(context, pageUrl);
        }
        return new CrazyShitRepository().resolvePlayable(context, pageUrl);
    }
}
