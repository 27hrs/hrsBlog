<script setup>
import AppIcon from './AppIcon.vue'

defineProps({
  links: { type: Array, default: () => [] },
  variant: { type: String, default: 'hero' },
})

function isExternal(href) {
  return /^https?:\/\//i.test(href || '')
}
</script>

<template>
  <nav class="socials" :class="`socials--${variant}`" aria-label="社交链接">
    <a
      v-for="link in links"
      :key="link.href"
      class="social"
      :href="link.href"
      :target="isExternal(link.href) ? '_blank' : undefined"
      :rel="isExternal(link.href) ? 'noopener noreferrer' : undefined"
    >
      <AppIcon :name="link.icon" :size="variant === 'footer' ? 15 : 14" />
      <span>{{ link.label }}</span>
      <AppIcon
        v-if="isExternal(link.href)"
        class="social__arrow"
        name="arrow-up-right"
        :size="11"
      />
    </a>
  </nav>
</template>

<style scoped>
.socials {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 22px;
}

.social {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--ink-soft);
  font-family: var(--font-mono);
  font-size: 10.5px;
  letter-spacing: 0.12em;
  line-height: 1;
  text-transform: uppercase;
  transition: color var(--dur-fast) var(--ease);
}

.social::after {
  position: absolute;
  bottom: -6px;
  left: 0;
  width: 100%;
  height: 1px;
  background: var(--copper);
  content: '';
  transform: scaleX(0);
  transform-origin: left;
  transition: transform var(--dur) var(--ease);
}

.social:hover {
  color: var(--copper);
}

.social:hover::after {
  transform: scaleX(1);
}

.social__arrow {
  opacity: 0.5;
}

.socials--footer {
  gap: 6px;
  flex-direction: column;
  align-items: flex-start;
}

.socials--footer .social {
  gap: 9px;
  padding: 5px 0;
  color: var(--on-dark-soft);
  font-family: var(--font-body);
  font-size: 13px;
  letter-spacing: 0.02em;
  line-height: 1.5;
  text-transform: none;
}

.socials--footer .social::after {
  bottom: 2px;
  background: var(--copper-bright);
}

.socials--footer .social:hover {
  color: var(--on-dark);
}

@media (max-width: 720px) {
  .socials {
    gap: 8px 18px;
  }
}
</style>
