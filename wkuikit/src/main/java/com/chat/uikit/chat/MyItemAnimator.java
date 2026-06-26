package com.chat.uikit.chat;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.view.View;
import android.view.ViewPropertyAnimator;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView ItemAnimator optimized for chat/message lists.
 *
 * It keeps lightweight add/remove/move animations, but intentionally disables change animations.
 * Change animations are easy to trigger in chat screens when avatars, unread counts, online status,
 * message status, translation state or payload content changes. Running a full change animation for
 * those updates can cause row flicker, short blank states, and ViewHolder state problems.
 */
public class MyItemAnimator extends SimpleItemAnimator {
    private static TimeInterpolator sDefaultInterpolator;

    private final ArrayList<RecyclerView.ViewHolder> mPendingRemovals = new ArrayList<>();
    private final ArrayList<RecyclerView.ViewHolder> mPendingAdditions = new ArrayList<>();
    private final ArrayList<MoveInfo> mPendingMoves = new ArrayList<>();

    private final ArrayList<ArrayList<RecyclerView.ViewHolder>> mAdditionsList = new ArrayList<>();
    private final ArrayList<ArrayList<MoveInfo>> mMovesList = new ArrayList<>();

    private final ArrayList<RecyclerView.ViewHolder> mAddAnimations = new ArrayList<>();
    private final ArrayList<RecyclerView.ViewHolder> mMoveAnimations = new ArrayList<>();
    private final ArrayList<RecyclerView.ViewHolder> mRemoveAnimations = new ArrayList<>();

    public MyItemAnimator() {
        setSupportsChangeAnimations(false);
    }

    private static class MoveInfo {
        final RecyclerView.ViewHolder holder;
        final int fromX;
        final int fromY;
        final int toX;
        final int toY;

        MoveInfo(RecyclerView.ViewHolder holder, int fromX, int fromY, int toX, int toY) {
            this.holder = holder;
            this.fromX = fromX;
            this.fromY = fromY;
            this.toX = toX;
            this.toY = toY;
        }
    }

    @Override
    public void runPendingAnimations() {
        final boolean removalsPending = !mPendingRemovals.isEmpty();
        final boolean movesPending = !mPendingMoves.isEmpty();
        final boolean additionsPending = !mPendingAdditions.isEmpty();

        if (!removalsPending && !movesPending && !additionsPending) {
            return;
        }

        for (RecyclerView.ViewHolder holder : mPendingRemovals) {
            animateRemoveImpl(holder);
        }
        mPendingRemovals.clear();

        if (movesPending) {
            final ArrayList<MoveInfo> moves = new ArrayList<>(mPendingMoves);
            mMovesList.add(moves);
            mPendingMoves.clear();

            final Runnable mover = new Runnable() {
                @Override
                public void run() {
                    for (MoveInfo moveInfo : moves) {
                        animateMoveImpl(moveInfo.holder, moveInfo.fromX, moveInfo.fromY,
                                moveInfo.toX, moveInfo.toY);
                    }
                    moves.clear();
                    mMovesList.remove(moves);
                }
            };

            if (removalsPending) {
                View view = moves.get(0).holder.itemView;
                ViewCompat.postOnAnimationDelayed(view, mover, getRemoveDuration());
            } else {
                mover.run();
            }
        }

        if (additionsPending) {
            final ArrayList<RecyclerView.ViewHolder> additions = new ArrayList<>(mPendingAdditions);
            mAdditionsList.add(additions);
            mPendingAdditions.clear();

            final Runnable adder = new Runnable() {
                @Override
                public void run() {
                    for (RecyclerView.ViewHolder holder : additions) {
                        animateAddImpl(holder);
                    }
                    additions.clear();
                    mAdditionsList.remove(additions);
                }
            };

            if (removalsPending || movesPending) {
                long removeDuration = removalsPending ? getRemoveDuration() : 0L;
                long moveDuration = movesPending ? getMoveDuration() : 0L;
                long totalDelay = removeDuration + moveDuration;
                View view = additions.get(0).itemView;
                ViewCompat.postOnAnimationDelayed(view, adder, totalDelay);
            } else {
                adder.run();
            }
        }
    }

    @Override
    @SuppressLint("UnknownNullness")
    public boolean animateRemove(final RecyclerView.ViewHolder holder) {
        resetAnimation(holder);
        mPendingRemovals.add(holder);
        return true;
    }

    private void animateRemoveImpl(final RecyclerView.ViewHolder holder) {
        final View view = holder.itemView;
        final ViewPropertyAnimator animation = view.animate();
        mRemoveAnimations.add(holder);

        animation.setDuration(getRemoveDuration())
                .alpha(0f)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animator) {
                        dispatchRemoveStarting(holder);
                    }

                    @Override
                    public void onAnimationCancel(Animator animator) {
                        view.setAlpha(1f);
                    }

                    @Override
                    public void onAnimationEnd(Animator animator) {
                        animation.setListener(null);
                        view.setAlpha(1f);
                        dispatchRemoveFinished(holder);
                        mRemoveAnimations.remove(holder);
                        dispatchFinishedWhenDone();
                    }
                })
                .start();
    }

    @Override
    @SuppressLint("UnknownNullness")
    public boolean animateAdd(final RecyclerView.ViewHolder holder) {
        resetAnimation(holder);
        holder.itemView.setAlpha(0f);
        mPendingAdditions.add(holder);
        return true;
    }

    private void animateAddImpl(final RecyclerView.ViewHolder holder) {
        final View view = holder.itemView;
        final ViewPropertyAnimator animation = view.animate();
        mAddAnimations.add(holder);

        animation.alpha(1f)
                .setDuration(getAddDuration())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animator) {
                        dispatchAddStarting(holder);
                    }

                    @Override
                    public void onAnimationCancel(Animator animator) {
                        view.setAlpha(1f);
                    }

                    @Override
                    public void onAnimationEnd(Animator animator) {
                        animation.setListener(null);
                        view.setAlpha(1f);
                        dispatchAddFinished(holder);
                        mAddAnimations.remove(holder);
                        dispatchFinishedWhenDone();
                    }
                })
                .start();
    }

    @Override
    @SuppressLint("UnknownNullness")
    public boolean animateMove(final RecyclerView.ViewHolder holder, int fromX, int fromY,
                               int toX, int toY) {
        final View view = holder.itemView;
        fromX += (int) view.getTranslationX();
        fromY += (int) view.getTranslationY();
        resetAnimation(holder);

        final int deltaX = toX - fromX;
        final int deltaY = toY - fromY;
        if (deltaX == 0 && deltaY == 0) {
            resetView(view);
            dispatchMoveFinished(holder);
            return false;
        }

        if (deltaX != 0) {
            view.setTranslationX(-deltaX);
        }
        if (deltaY != 0) {
            view.setTranslationY(-deltaY);
        }
        mPendingMoves.add(new MoveInfo(holder, fromX, fromY, toX, toY));
        return true;
    }

    private void animateMoveImpl(final RecyclerView.ViewHolder holder,
                                 int fromX, int fromY, int toX, int toY) {
        final View view = holder.itemView;
        final int deltaX = toX - fromX;
        final int deltaY = toY - fromY;
        final ViewPropertyAnimator animation = view.animate();

        if (deltaX != 0) {
            animation.translationX(0f);
        }
        if (deltaY != 0) {
            animation.translationY(0f);
        }

        mMoveAnimations.add(holder);
        animation.setDuration(getMoveDuration())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animator) {
                        dispatchMoveStarting(holder);
                    }

                    @Override
                    public void onAnimationCancel(Animator animator) {
                        if (deltaX != 0) {
                            view.setTranslationX(0f);
                        }
                        if (deltaY != 0) {
                            view.setTranslationY(0f);
                        }
                    }

                    @Override
                    public void onAnimationEnd(Animator animator) {
                        animation.setListener(null);
                        view.setTranslationX(0f);
                        view.setTranslationY(0f);
                        dispatchMoveFinished(holder);
                        mMoveAnimations.remove(holder);
                        dispatchFinishedWhenDone();
                    }
                })
                .start();
    }

    /**
     * Change animations are intentionally disabled.
     *
     * If RecyclerView calls animateChange(), this animator still owns the completion callback.
     * Therefore we immediately finish the change after resetting old/new holders. This avoids the
     * old half-disabled implementation where the new holder could stay alpha=0 briefly, and it also
     * avoids leaving RecyclerView waiting for a change animation that will never run.
     */
    @Override
    @SuppressLint("UnknownNullness")
    public boolean animateChange(RecyclerView.ViewHolder oldHolder,
                                 RecyclerView.ViewHolder newHolder,
                                 int fromLeft, int fromTop, int toLeft, int toTop) {
        if (oldHolder == newHolder) {
            if (oldHolder == null) {
                dispatchFinishedWhenDone();
                return false;
            }
            return animateMove(oldHolder, fromLeft, fromTop, toLeft, toTop);
        }

        if (oldHolder != null) {
            resetAnimation(oldHolder);
            resetView(oldHolder.itemView);
            dispatchChangeStarting(oldHolder, true);
            dispatchChangeFinished(oldHolder, true);
        }

        if (newHolder != null) {
            resetAnimation(newHolder);
            resetView(newHolder.itemView);
            dispatchChangeStarting(newHolder, false);
            dispatchChangeFinished(newHolder, false);
        }

        dispatchFinishedWhenDone();
        return false;
    }

    @Override
    @SuppressLint("UnknownNullness")
    public void endAnimation(RecyclerView.ViewHolder item) {
        final View view = item.itemView;
        view.animate().cancel();

        for (int i = mPendingMoves.size() - 1; i >= 0; i--) {
            MoveInfo moveInfo = mPendingMoves.get(i);
            if (moveInfo.holder == item) {
                view.setTranslationX(0f);
                view.setTranslationY(0f);
                dispatchMoveFinished(item);
                mPendingMoves.remove(i);
            }
        }

        if (mPendingRemovals.remove(item)) {
            view.setAlpha(1f);
            dispatchRemoveFinished(item);
        }

        if (mPendingAdditions.remove(item)) {
            view.setAlpha(1f);
            dispatchAddFinished(item);
        }

        for (int i = mMovesList.size() - 1; i >= 0; i--) {
            ArrayList<MoveInfo> moves = mMovesList.get(i);
            for (int j = moves.size() - 1; j >= 0; j--) {
                MoveInfo moveInfo = moves.get(j);
                if (moveInfo.holder == item) {
                    view.setTranslationX(0f);
                    view.setTranslationY(0f);
                    dispatchMoveFinished(item);
                    moves.remove(j);
                    break;
                }
            }
            if (moves.isEmpty()) {
                mMovesList.remove(i);
            }
        }

        for (int i = mAdditionsList.size() - 1; i >= 0; i--) {
            ArrayList<RecyclerView.ViewHolder> additions = mAdditionsList.get(i);
            if (additions.remove(item)) {
                view.setAlpha(1f);
                dispatchAddFinished(item);
            }
            if (additions.isEmpty()) {
                mAdditionsList.remove(i);
            }
        }

        if (mRemoveAnimations.remove(item)) {
            view.setAlpha(1f);
        }
        if (mAddAnimations.remove(item)) {
            view.setAlpha(1f);
        }
        if (mMoveAnimations.remove(item)) {
            view.setTranslationX(0f);
            view.setTranslationY(0f);
        }

        resetView(view);
        dispatchFinishedWhenDone();
    }

    private void resetAnimation(RecyclerView.ViewHolder holder) {
        if (sDefaultInterpolator == null) {
            sDefaultInterpolator = new ValueAnimator().getInterpolator();
        }
        holder.itemView.animate().setInterpolator(sDefaultInterpolator);
        endAnimation(holder);
    }

    private void resetView(View view) {
        view.setAlpha(1f);
        view.setTranslationX(0f);
        view.setTranslationY(0f);
    }

    @Override
    public boolean isRunning() {
        return !mPendingAdditions.isEmpty()
                || !mPendingMoves.isEmpty()
                || !mPendingRemovals.isEmpty()
                || !mMoveAnimations.isEmpty()
                || !mRemoveAnimations.isEmpty()
                || !mAddAnimations.isEmpty()
                || !mMovesList.isEmpty()
                || !mAdditionsList.isEmpty();
    }

    private void dispatchFinishedWhenDone() {
        if (!isRunning()) {
            dispatchAnimationsFinished();
        }
    }

    @Override
    public void endAnimations() {
        for (int i = mPendingMoves.size() - 1; i >= 0; i--) {
            MoveInfo moveInfo = mPendingMoves.get(i);
            View view = moveInfo.holder.itemView;
            view.setTranslationX(0f);
            view.setTranslationY(0f);
            dispatchMoveFinished(moveInfo.holder);
            mPendingMoves.remove(i);
        }

        for (int i = mPendingRemovals.size() - 1; i >= 0; i--) {
            RecyclerView.ViewHolder holder = mPendingRemovals.get(i);
            holder.itemView.setAlpha(1f);
            dispatchRemoveFinished(holder);
            mPendingRemovals.remove(i);
        }

        for (int i = mPendingAdditions.size() - 1; i >= 0; i--) {
            RecyclerView.ViewHolder holder = mPendingAdditions.get(i);
            holder.itemView.setAlpha(1f);
            dispatchAddFinished(holder);
            mPendingAdditions.remove(i);
        }

        for (int i = mMovesList.size() - 1; i >= 0; i--) {
            ArrayList<MoveInfo> moves = mMovesList.get(i);
            for (int j = moves.size() - 1; j >= 0; j--) {
                MoveInfo moveInfo = moves.get(j);
                View view = moveInfo.holder.itemView;
                view.setTranslationX(0f);
                view.setTranslationY(0f);
                dispatchMoveFinished(moveInfo.holder);
                moves.remove(j);
            }
            if (moves.isEmpty()) {
                mMovesList.remove(i);
            }
        }

        for (int i = mAdditionsList.size() - 1; i >= 0; i--) {
            ArrayList<RecyclerView.ViewHolder> additions = mAdditionsList.get(i);
            for (int j = additions.size() - 1; j >= 0; j--) {
                RecyclerView.ViewHolder holder = additions.get(j);
                holder.itemView.setAlpha(1f);
                dispatchAddFinished(holder);
                additions.remove(j);
            }
            if (additions.isEmpty()) {
                mAdditionsList.remove(i);
            }
        }

        cancelAll(mRemoveAnimations);
        cancelAll(mMoveAnimations);
        cancelAll(mAddAnimations);

        mRemoveAnimations.clear();
        mMoveAnimations.clear();
        mAddAnimations.clear();

        dispatchAnimationsFinished();
    }

    private void cancelAll(List<RecyclerView.ViewHolder> viewHolders) {
        for (int i = viewHolders.size() - 1; i >= 0; i--) {
            RecyclerView.ViewHolder holder = viewHolders.get(i);
            if (holder != null) {
                holder.itemView.animate().cancel();
                resetView(holder.itemView);
            }
        }
    }

    /**
     * Always reuse updated holders to avoid full change animations and row flicker.
     * Adapter payload updates are still delivered normally by RecyclerView.
     */
    @Override
    public boolean canReuseUpdatedViewHolder(@NonNull RecyclerView.ViewHolder viewHolder,
                                             @NonNull List<Object> payloads) {
        return true;
    }
}
