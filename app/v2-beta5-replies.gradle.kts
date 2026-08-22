// Beta 5 native comment and reply composer.
// Applied after the existing v2 runtime bridge so the proven parser/login flow stays intact.

tasks.register("wireV2Beta5Replies") {
    dependsOn("wireV2BetaRuntime")
    doLast {
        val commentsFile = file("src/main/java/com/webapp/crazyshit/CommentsActivity.java")
        var comments = commentsFile.readText()
        if (comments.contains("BETA5_NATIVE_REPLIES")) return@doLast

        comments = comments.replace(
            "import android.app.Activity;\n",
            "import android.app.Activity;\nimport android.app.AlertDialog;\n"
        )
        comments = comments.replace(
            "import android.widget.FrameLayout;\n",
            "import android.widget.EditText;\nimport android.widget.FrameLayout;\n"
        )
        comments = comments.replace(
            "import android.widget.TextView;\n",
            "import android.widget.TextView;\nimport android.widget.Toast;\n"
        )

        comments = comments.replace(
            """        for (CommentItem item : display) {
            commentsContainer.addView(
                    commentCard(item.author, item.time, item.text, item.avatar, item.score),
                    commentCardParams(item.depth)
            );
        }
""",
            """        if (!lastLoginRequired) {
            commentsContainer.addView(composeCard(), cardParams());
        }

        for (CommentItem item : display) {
            commentsContainer.addView(
                    commentCard(item),
                    commentCardParams(item.depth)
            );
        }
"""
        )

        comments = comments.replace(
            """    private View commentCard(String author, String time, String text, String avatarUrl, String score) {
        MaterialCardView card = new MaterialCardView(this);
""",
            """    // BETA5_NATIVE_REPLIES
    private View commentCard(CommentItem item) {
        String author = item.author;
        String time = item.time;
        String text = item.text;
        String avatarUrl = item.avatar;
        String score = item.score;
        MaterialCardView card = new MaterialCardView(this);
"""
        )

        comments = comments.replace(
            """        copy.setTextIsSelectable(true);
        body.addView(copy, new LinearLayout.LayoutParams(-1, -2));
        return card;
    }

    private LinearLayout.LayoutParams cardParams() {
""",
            """        copy.setTextIsSelectable(true);
        body.addView(copy, new LinearLayout.LayoutParams(-1, -2));

        if (!lastLoginRequired) {
            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            actions.setPadding(0, dp(8), 0, 0);
            body.addView(actions, new LinearLayout.LayoutParams(-1, -2));

            TextView reply = new TextView(this);
            reply.setText("REPLY");
            reply.setTextColor(Color.rgb(255, 112, 60));
            reply.setTextSize(11);
            reply.setTypeface(null, android.graphics.Typeface.BOLD);
            reply.setPadding(0, dp(6), dp(20), dp(6));
            reply.setOnClickListener(v -> showComposer(item));
            actions.addView(reply, new LinearLayout.LayoutParams(-2, -2));
        }
        return card;
    }

    private View composeCard() {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(Color.rgb(31, 25, 23));
        card.setStrokeColor(Color.rgb(92, 48, 33));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(15));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> showComposer(null));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.addView(row, new MaterialCardView.LayoutParams(-1, -2));

        TextView prompt = new TextView(this);
        prompt.setText("Write a comment…");
        prompt.setTextColor(Color.rgb(210, 210, 216));
        prompt.setTextSize(14);
        row.addView(prompt, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView post = new TextView(this);
        post.setText("POST");
        post.setTextColor(Color.rgb(255, 112, 60));
        post.setTextSize(11);
        post.setTypeface(null, android.graphics.Typeface.BOLD);
        row.addView(post, new LinearLayout.LayoutParams(-2, -2));
        return card;
    }

    private void showComposer(CommentItem replyTo) {
        if (lastLoginRequired) {
            openLogin();
            return;
        }

        final EditText input = new EditText(this);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.rgb(145, 145, 154));
        input.setHint(replyTo == null ? "Write a comment" : "Reply to " + (replyTo.author.isEmpty() ? "comment" : replyTo.author));
        input.setMinLines(3);
        input.setMaxLines(8);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setBackgroundColor(Color.rgb(28, 28, 32));

        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(dp(18), dp(4), dp(18), 0);
        wrap.addView(input, new LinearLayout.LayoutParams(-1, -2));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(replyTo == null ? "New comment" : "Reply")
                .setMessage(replyTo == null ? "Post to this video" : "Reply to " + (replyTo.author.isEmpty() ? "this comment" : replyTo.author))
                .setView(wrap)
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("POST", null)
                .create();

        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String message = input.getText() == null ? "" : input.getText().toString().trim();
            if (message.length() < 2) {
                input.setError("Write something first");
                return;
            }
            dialog.dismiss();
            submitNativeComment(message, replyTo);
        }));
        dialog.show();
    }

    private void submitNativeComment(String message, CommentItem replyTo) {
        if (extractor == null) return;
        Toast.makeText(this, replyTo == null ? "Posting comment…" : "Opening reply…", Toast.LENGTH_SHORT).show();
        if (replyTo == null) {
            submitToCommentForm(message, false);
            return;
        }

        String targetText = JSONObject.quote(replyTo.text.length() > 140 ? replyTo.text.substring(0, 140) : replyTo.text);
        String targetAuthor = JSONObject.quote(replyTo.author);
        String openReplyJs = "(() => {" +
                "const clean=s=>(s||'').replace(/\\s+/g,' ').trim();" +
                "const wanted=" + targetText + ";const author=" + targetAuthor + ";" +
                "const roots=[...document.querySelectorAll('[data-comment-id],[id^=comment-],[id^=comment_],.comment,.comment-item,.comment_item,[class*=comment]')];" +
                "let target=roots.find(e=>{let t=clean(e.innerText||e.textContent);return t.includes(wanted)&&(!author||t.includes(author));});" +
                "if(!target)target=roots.find(e=>clean(e.innerText||e.textContent).includes(wanted));" +
                "if(!target)return 'not-found';" +
                "target.setAttribute('data-jeremy-reply-target','1');" +
                "const controls=[...target.querySelectorAll('button,a,input[type=button],input[type=submit]')];" +
                "const reply=controls.find(x=>/reply/i.test(clean(x.innerText||x.value||x.getAttribute('aria-label')||x.title||''))||/reply/i.test((x.className||'')+' '+(x.id||'')));" +
                "if(!reply)return 'no-reply';reply.click();return 'clicked';" +
                "})()";
        try {
            extractor.evaluateJavascript(openReplyJs, raw -> {
                String result = raw == null ? "" : raw.replace("\\\"", "\"");
                if (!result.contains("clicked")) {
                    Toast.makeText(this, "Couldn't open that reply box. Try WEB for this comment.", Toast.LENGTH_LONG).show();
                    return;
                }
                extractor.postDelayed(() -> submitToCommentForm(message, true), 450L);
            });
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't start reply.", Toast.LENGTH_SHORT).show();
        }
    }

    private void submitToCommentForm(String message, boolean replying) {
        if (extractor == null) return;
        String text = JSONObject.quote(message);
        String js = "(() => {" +
                "const msg=" + text + ";" +
                "const scope=(" + (replying ? "document.querySelector('[data-jeremy-reply-target=\\\"1\\\"]')" : "document") + ")||document;" +
                "const sels=['textarea[name*=comment i]','textarea[name*=message i]','textarea[id*=comment i]','textarea[class*=comment i]','textarea'];" +
                "let ta=null;for(const s of sels){ta=scope.querySelector(s);if(ta)break;}" +
                "if(!ta&&scope!==document){for(const s of sels){let all=[...document.querySelectorAll(s)];if(all.length){ta=all[all.length-1];break;}}}" +
                "if(!ta)return JSON.stringify({ok:false,reason:'textarea'});" +
                "const setter=Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value');if(setter&&setter.set)setter.set.call(ta,msg);else ta.value=msg;" +
                "ta.dispatchEvent(new Event('input',{bubbles:true}));ta.dispatchEvent(new Event('change',{bubbles:true}));" +
                "const form=ta.closest('form');if(!form)return JSON.stringify({ok:false,reason:'form'});" +
                "let btn=form.querySelector('button[type=submit],input[type=submit]');" +
                "if(!btn){btn=[...form.querySelectorAll('button,input')].find(x=>/post|submit|send|comment|reply/i.test((x.innerText||x.value||x.getAttribute('aria-label')||'')));}" +
                "if(btn){btn.click();return JSON.stringify({ok:true,method:'click'});}" +
                "if(form.requestSubmit){form.requestSubmit();return JSON.stringify({ok:true,method:'requestSubmit'});}" +
                "return JSON.stringify({ok:false,reason:'submit'});" +
                "})()";
        try {
            extractor.evaluateJavascript(js, raw -> {
                String result = raw == null ? "" : raw;
                if (result.contains("\\\"ok\\\":true") || result.contains("\"ok\":true")) {
                    Toast.makeText(this, "Posted. Refreshing comments…", Toast.LENGTH_SHORT).show();
                    extractor.postDelayed(this::loadComments, 1400L);
                } else {
                    Toast.makeText(this, "The site didn't accept the native form. Tap WEB to finish there.", Toast.LENGTH_LONG).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't submit comment.", Toast.LENGTH_SHORT).show();
        }
    }

    private LinearLayout.LayoutParams cardParams() {
"""
        )

        commentsFile.writeText(comments)
    }
}

tasks.matching {
    it.name == "preDebugBuild" || it.name == "preReleaseBuild"
}.configureEach {
    dependsOn("wireV2Beta5Replies")
}
