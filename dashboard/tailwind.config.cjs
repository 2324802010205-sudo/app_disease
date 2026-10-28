module.exports = {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        forest: { 900: '#15472F', 800: '#1F5E3E', 700: '#2E7D50' },
        ink: '#0F3B2B',
        canvas: '#F8FAF8',
      },
      fontFamily: { sans: ['Be Vietnam Pro', 'Arial', 'sans-serif'] },
    },
  },
  plugins: [],
}
