package com.apex.web.controllers;

import com.apex.App;
import com.apex.util.Json;
import com.apex.util.Validation;
import com.apex.web.ApiException;
import com.apex.web.HttpUtil;
import com.apex.web.Router;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Payments + wallet:
 *  POST /api/payments/submit        - customer submits receipt (Pending only)
 *  GET  /api/payments               - admin list
 *  POST /api/payments/{id}/review   - admin verify / reject / reupload
 *  POST /api/payments/record        - admin records an offline payment
 *  GET  /api/wallet                 - admin wallet + owner wallets + ledger
 *  POST /api/wallet/add-cash        - admin adds cash
 *  POST /api/wallet/withdraw        - admin pays out
 */
public final class PaymentController {

    private final App app;

    public PaymentController(App app) { this.app = app; }

    public void register(Router r) {
        r.get("/api/payments", Router.Level.ADMIN, ctx -> {
            JsonArray a = new JsonArray();
            for (JsonObject p : app.payments.list()) a.add(p);
            HttpUtil.sendJson(ctx.ex, 200, a);
        });

        r.post("/api/payments/submit", Router.Level.ANY, ctx -> {
            JsonObject o = app.paymentService.submit(ctx.session, ctx.body);
            HttpUtil.sendJson(ctx.ex, 201, o);
        });

        r.post("/api/payments/{id}/review", Router.Level.ADMIN, ctx -> {
            long id = idParam(ctx);
            JsonObject o = app.paymentService.review(ctx.session, id, ctx.body);
            HttpUtil.sendJson(ctx.ex, 200, o);
        });

        r.post("/api/payments/record", Router.Level.ADMIN, ctx -> {
            JsonObject o = app.paymentService.record(ctx.session, ctx.body);
            HttpUtil.sendJson(ctx.ex, 200, o);
        });

        r.get("/api/wallet", Router.Level.ADMIN, ctx -> {
            HttpUtil.sendJson(ctx.ex, 200, app.paymentService.walletInfo());
        });

        r.get("/api/wallet/mine", Router.Level.ANY, ctx -> {
            String userId = Json.getStr(ctx.session, "userId", "");
            long balance = app.db.with(c -> app.wallet.ownerBalance(c, userId));
            JsonObject result = new JsonObject();
            result.addProperty("balance", balance);
            HttpUtil.sendJson(ctx.ex, 200, result);
        });

        r.post("/api/wallet/add-cash", Router.Level.ADMIN, ctx -> {
            JsonObject o = app.paymentService.addCash(ctx.session, ctx.body);
            HttpUtil.sendJson(ctx.ex, 200, o);
        });

        r.post("/api/wallet/withdraw", Router.Level.ADMIN, ctx -> {
            JsonObject o = app.paymentService.withdraw(ctx.session, ctx.body);
            HttpUtil.sendJson(ctx.ex, 200, o);
        });
    }

    private static long idParam(Router.Ctx ctx) {
        String last = Json.clean(ctx.param("id"));
        try {
            return Long.parseLong(last);
        } catch (NumberFormatException e) {
            throw ApiException.bad("Invalid payment id");
        }
    }
}
