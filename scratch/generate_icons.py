from PIL import Image
import os

def create_icons(input_png, output_dir):
    img = Image.open(input_png)
    
    # Create .ico
    ico_path = os.path.join(output_dir, "icon.ico")
    img.save(ico_path, format='ICO', sizes=[(16, 16), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
    print(f"Created {ico_path}")
    
    # Create .icns
    icns_path = os.path.join(output_dir, "icon.icns")
    # ICNS usually needs specific sizes: 16, 32, 64, 128, 256, 512, 1024
    # Pillow's ICNS support is decent but let's provide as many as possible
    img.save(icns_path, format='ICNS')
    print(f"Created {icns_path}")

if __name__ == "__main__":
    input_file = "/projects/Java Projects/lms-javafx/src/main/resources/icon.png"
    output_folder = "/projects/Java Projects/lms-javafx/src/main/resources"
    create_icons(input_file, output_folder)
