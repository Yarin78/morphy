package se.yarin.cbhlib.validation;


import me.tongfei.progressbar.ProgressBar;

interface ProgressTracker extends AutoCloseable {
    void step();

    void close();

    class DummyTracker implements ProgressTracker {

        @Override
        public void step() {}

        @Override
        public void close() {}
    }

    class ProgressBarTracker implements ProgressTracker {
        private final ProgressBar progressBar;

        public ProgressBarTracker(ProgressBar progressBar) {
            this.progressBar = progressBar;
        }

        @Override
        public void step() {
            this.progressBar.step();
        }

        @Override
        public void close() {
            this.progressBar.close();
        }
    }
}
