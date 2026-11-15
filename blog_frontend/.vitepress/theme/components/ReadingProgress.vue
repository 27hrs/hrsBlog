<template>
  <div class="reading-progress" aria-hidden="true">
    <span class="reading-progress__bar" />
  </div>
</template>

<style scoped>
.reading-progress {
  position: fixed;
  top: var(--header-h);
  right: 0;
  left: 0;
  z-index: 55;
  height: 1px;
  pointer-events: none;
}

.reading-progress__bar {
  display: block;
  width: 100%;
  height: 100%;
  background: var(--copper);
  transform: scaleX(0);
  transform-origin: 0 50%;
  animation: reading-progress linear both;
  animation-timeline: scroll(root);
}

@keyframes reading-progress {
  from {
    transform: scaleX(0);
  }

  to {
    transform: scaleX(1);
  }
}

@supports not (animation-timeline: scroll()) {
  .reading-progress {
    display: none;
  }
}
</style>
