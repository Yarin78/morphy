package se.yarin.morphy.validation;


import me.tongfei.progressbar.ProgressBar;

interface TrackerFactory {
    ProgressTracker create(String task, long initialMax);

    class ProgressBarTrackerFactory implements TrackerFactory {
        public ProgressTracker create(String task, long initialMax) {
            return new ProgressTracker.ProgressBarTracker(new ProgressBar(task, initialMax));
        }
    }

    class DummyTrackerFactory implements TrackerFactory {
        public ProgressTracker create(String task, long initialMax) {
            return new ProgressTracker.DummyTracker();
        }
    }
}
