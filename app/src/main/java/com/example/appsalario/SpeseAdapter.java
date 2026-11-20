package com.example.appsalario;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;

public final class SpeseAdapter extends RecyclerView.Adapter<SpeseAdapter.SpesaViewHolder> {
    private static final Object PAYLOAD_TICK = new Object();
    private static final Object PAYLOAD_UTILIZZI = new Object();

    private List<Spesa> spese = new ArrayList<Spesa>();
    private Map<String, Ripago.Piano> piani = Collections.emptyMap();
    private final float oreGiorno;
    private final Function1<Spesa, Unit> onRigaClick;
    private String daEvidenziare;
    private Function1<Spesa, Unit> onUtilizzo;

    /** Chiamato quando si tocca "+1" sulla riga utilizzi. */
    private Function1<Spesa, Unit> onUtilizzoMeno;

    public void setOnUtilizzoMeno(Function1<Spesa, Unit> f) {
        onUtilizzoMeno = f;
    }

    public void setOnUtilizzo(Function1<Spesa, Unit> f) {
        onUtilizzo = f;
    }
    private final NumberFormat valuta = NumberFormat.getCurrencyInstance(Locale.ITALY);

    public SpeseAdapter(float oreGiorno, Function1<Spesa, Unit> onRigaClick) {
        this.oreGiorno = oreGiorno;
        this.onRigaClick = onRigaClick;
    }

    public static final class SpesaViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView cardView;
        final ImageView ivApprovata;
        final ImageView ivPromemoria;
        final TextView tvImporto;
        final TextView tvDataSpesa;
        final View boxUtilizzi;
        final TextView tvUtilizzi;
        final TextView tvCostoUso;
        final TextView btnPiuUno;
        final TextView btnMenoUno;
        final TextView tvNomeSpesa;
        final TextView tvOreNecessarie;
        final View tileCategoria;
        final ImageView ivCategoria;
        final View boxRipago;
        final ProgressBar pbRipago;
        final ImageView ivStatoRipago;
        final TextView tvStatoRipago;
        final TextView tvPercentualeRipago;

        SpesaViewHolder(View v) {
            super(v);
            cardView = v.findViewById(R.id.cardView);
            tvNomeSpesa = v.findViewById(R.id.tvNomeSpesa);
            tvImporto = v.findViewById(R.id.tvImporto);
            tvDataSpesa = v.findViewById(R.id.tvDataSpesa);
            boxUtilizzi = v.findViewById(R.id.boxUtilizzi);
            tvUtilizzi = v.findViewById(R.id.tvUtilizzi);
            tvCostoUso = v.findViewById(R.id.tvCostoUso);
            btnPiuUno = v.findViewById(R.id.btnPiuUno);
            btnMenoUno = v.findViewById(R.id.btnMenoUno);
            tvOreNecessarie = v.findViewById(R.id.tvOreNecessarie);
            ivApprovata = v.findViewById(R.id.ivApprovata);
            ivPromemoria = v.findViewById(R.id.ivPromemoria);
            tileCategoria = v.findViewById(R.id.tileCategoria);
            ivCategoria = v.findViewById(R.id.ivCategoria);
            boxRipago = v.findViewById(R.id.boxRipago);
            pbRipago = v.findViewById(R.id.pbRipago);
            ivStatoRipago = v.findViewById(R.id.ivStatoRipago);
            tvStatoRipago = v.findViewById(R.id.tvStatoRipago);
            tvPercentualeRipago = v.findViewById(R.id.tvPercentualeRipago);
        }
    }

    @Override
    public SpesaViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_spesa, parent, false);
        SpesaViewHolder holder = new SpesaViewHolder(view);
        Premibile.INSTANCE.applicaA(holder.cardView);
        return holder;
    }

    @Override
    public void onBindViewHolder(SpesaViewHolder holder, int position, List<Object> payloads) {
        if (!payloads.isEmpty() && (payloads.contains(PAYLOAD_TICK) || payloads.contains(PAYLOAD_UTILIZZI))) {
            Spesa s = spese.get(position);
            if (payloads.contains(PAYLOAD_UTILIZZI)) {
                bindUtilizzi(holder, s);
                Privacy.sfoca(holder.itemView.getContext(), holder.tvCostoUso);
            }
            bindRipago(holder, s);
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    @Override
    public void onBindViewHolder(SpesaViewHolder holder, int position) {
        final Spesa spesa = spese.get(position);
        Context ctx = holder.itemView.getContext();
        holder.tvNomeSpesa.setText(spesa.getNome());
        CategoriaPicker.applica(holder.tileCategoria, holder.ivCategoria, Categorie.get(ctx, spesa.getCategoria()));
        holder.tvImporto.setText(valuta.format(spesa.getImporto()));
        holder.tvDataSpesa.setText(" · " + Ripago.data(spesa.getDataCreazione()));
        bindUtilizzi(holder, spesa);
        Privacy.sfoca(ctx, holder.tvImporto, holder.tvCostoUso);
        holder.tvOreNecessarie.setText(TempoFormatter.INSTANCE.formatta(ctx, spesa.getOreNecessarie(), oreGiorno));
        holder.ivApprovata.setVisibility(spesa.getApprovata() ? View.VISIBLE : View.GONE);
        holder.ivPromemoria.setVisibility(spesa.getNotificaAttiva() ? View.VISIBLE : View.GONE);
        holder.cardView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onRigaClick.invoke(spesa);
            }
        });
        bindRipago(holder, spesa);
        if (spesa.getId().equals(daEvidenziare)) {
            daEvidenziare = null;
            evidenzia(holder.cardView);
        }
    }

    private void bindUtilizzi(final SpesaViewHolder h, final Spesa spesa) {
        if (!spesa.getTracciaUtilizzi()) {
            h.boxUtilizzi.setVisibility(View.GONE);
            h.btnPiuUno.setVisibility(View.GONE);
            h.btnPiuUno.setOnClickListener(null);
            h.btnMenoUno.setVisibility(View.GONE);
            h.btnMenoUno.setOnClickListener(null);
            return;
        }
        Context ctx = h.itemView.getContext();
        h.boxUtilizzi.setVisibility(View.VISIBLE);
        h.btnPiuUno.setVisibility(View.VISIBLE);
        h.btnMenoUno.setVisibility(View.VISIBLE);
        int n = spesa.getUtilizzi();
        h.btnMenoUno.setEnabled(n > 0);
        h.btnMenoUno.setAlpha(n > 0 ? 1f : 0.35f);
        if (n == 0) {
            h.tvUtilizzi.setText(R.string.nessun_utilizzo);
            h.tvCostoUso.setText("");
        } else {
            String tempo = TempoFormatter.INSTANCE.formatta(ctx, spesa.getOreNecessarie() / n, oreGiorno);
            h.tvUtilizzi.setText(ctx.getResources().getQuantityString(R.plurals.utilizzi_n, n, n) + ctx.getString(R.string.a_uso, tempo));
            h.tvCostoUso.setText(ctx.getString(R.string.costo_a_uso, valuta.format(spesa.getImporto() / n)));
        }
        h.btnPiuUno.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                v.animate().cancel();
                v.setScaleX(0.85f);
                v.setScaleY(0.85f);
                v.animate().scaleX(1f).scaleY(1f).setDuration(260)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(3f)).start();
                if (onUtilizzo != null) onUtilizzo.invoke(spesa);
            }
        });
        h.btnMenoUno.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (spesa.getUtilizzi() <= 0) return;
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                v.animate().cancel();
                v.setScaleX(0.85f);
                v.setScaleY(0.85f);
                v.animate().scaleX(1f).scaleY(1f).setDuration(260)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(3f)).start();
                if (onUtilizzoMeno != null) onUtilizzoMeno.invoke(spesa);
            }
        });
    }

    private void bindRipago(SpesaViewHolder h, Spesa spesa) {
        Ripago.Piano piano = spesa.getApprovata() ? piani.get(spesa.getId()) : null;
        if (piano == null) {
            h.boxRipago.setVisibility(View.GONE);
            return;
        }
        Context ctx = h.itemView.getContext();
        h.boxRipago.setVisibility(View.VISIBLE);
        long adesso = System.currentTimeMillis();
        int stato = piano.stato(adesso);
        int verde = ContextCompat.getColor(ctx, R.color.tempo);
        int grigio = ContextCompat.getColor(ctx, R.color.text_secondary);
        int arancio = ContextCompat.getColor(ctx, R.color.warning);

        h.pbRipago.setProgress(Math.round(piano.progresso(adesso) * 1000f));
        switch (stato) {
            case Ripago.COMPLETATA:
                h.ivStatoRipago.setImageResource(R.drawable.ic_checkmark_circle);
                h.ivStatoRipago.setImageTintList(ColorStateList.valueOf(verde));
                h.tvStatoRipago.setTextColor(verde);
                h.tvStatoRipago.setText(ctx.getString(R.string.ripago_completato, Ripago.data(piano.fine)));
                h.tvPercentualeRipago.setText("");
                h.tvPercentualeRipago.setVisibility(View.GONE);
                break;
            case Ripago.IN_CORSO:
                boolean pausa = piano.inPausa(adesso);
                String pct = (int) Math.floor(piano.progresso(adesso) * 100f) + "%";
                h.ivStatoRipago.setImageResource(R.drawable.ic_hourglass);
                h.ivStatoRipago.setImageTintList(ColorStateList.valueOf(pausa ? grigio : verde));
                h.tvStatoRipago.setTextColor(grigio);
                h.tvStatoRipago.setText(ctx.getString(R.string.ripago_tra, Ripago.formattaRestante(piano.restante(adesso))));
                h.tvPercentualeRipago.setVisibility(View.VISIBLE);
                h.tvPercentualeRipago.setText(pausa ? ctx.getString(R.string.ripago_in_pausa, pct) : pct);
                break;
            case Ripago.IN_CODA:
                h.ivStatoRipago.setImageResource(R.drawable.ic_hourglass);
                h.ivStatoRipago.setImageTintList(ColorStateList.valueOf(arancio));
                h.tvStatoRipago.setTextColor(grigio);
                h.tvStatoRipago.setText(ctx.getString(R.string.ripago_in_coda, Ripago.dataOra(piano.inizio)));
                h.tvPercentualeRipago.setVisibility(View.GONE);
                break;
            default: // FUTURA
                h.ivStatoRipago.setImageResource(R.drawable.ic_calendar);
                h.ivStatoRipago.setImageTintList(ColorStateList.valueOf(grigio));
                h.tvStatoRipago.setTextColor(grigio);
                h.tvStatoRipago.setText(ctx.getString(R.string.ripago_futuro, Ripago.dataOra(piano.inizio)));
                h.tvPercentualeRipago.setVisibility(View.GONE);
                break;
        }
    }

    /** Aggiorna solo il countdown delle righe (chiamato ogni secondo). */
    public void tick() {
        if (!piani.isEmpty() && !spese.isEmpty()) notifyItemRangeChanged(0, spese.size(), PAYLOAD_TICK);
    }

    private void evidenzia(View card) {
        card.setAlpha(0.35f);
        card.animate().alpha(1.0f).setDuration(900L)
                .setInterpolator(AnimationUtils.loadInterpolator(card.getContext(), R.interpolator.ios_ease_out)).start();
    }

    @Override
    public int getItemCount() {
        return spese.size();
    }

    public Spesa spesaAt(int posizione) {
        return spese.get(posizione);
    }

    public void updateSpese(List<Spesa> nuove, Map<String, Ripago.Piano> nuoviPiani) {
        spese = nuove;
        piani = nuoviPiani;
        notifyDataSetChanged();
    }

    /** Aggiorna gli utilizzi di una riga senza ricaricare la lista. */
    public void aggiornaUtilizzi(String spesaId, int utilizzi) {
        int p = posizioneDi(spesaId);
        if (p == -1) return;
        spese.get(p).setUtilizzi(utilizzi);
        notifyItemChanged(p, PAYLOAD_UTILIZZI);
    }

    public int posizioneDi(String spesaId) {
        for (int i = 0; i < spese.size(); i++) if (spese.get(i).getId().equals(spesaId)) return i;
        return -1;
    }

    public void evidenziaSpesa(String spesaId) {
        daEvidenziare = spesaId;
        int p = posizioneDi(spesaId);
        if (p != -1) notifyItemChanged(p);
    }
}
