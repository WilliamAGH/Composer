const forms = require('@tailwindcss/forms');
const typography = require('@tailwindcss/typography');

module.exports = {
  content: [
    './index.html',
    './src/**/*.{js,ts,svelte}',
    '../src/main/resources/templates/**/*.{html,htm}'
  ],
  theme: {
    extend: {}
  },
  plugins: [forms, typography]
};
